package shimeserver.shimepluginlibrary.shimescoreboard

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimescheduler.ShimeTaskHandle
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * プレイヤーごとのサイドバースコアボードマネージャー。
 *
 * 各プレイヤーは独立したスコアボードを受け取る。行はチーム経由で描画されるため、変更された
 * プレフィックスだけがサーバーから送信され、変更のない更新は完全にスキップされる。
 */
class ShimeScoreboardManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger,
    private val scheduler: ShimeScheduler
) : ShimeAbstractService(SERVICE_NAME), Listener {
    private data class Session(
        val sidebar: ShimeSidebar,
        val scoreboard: Scoreboard,
        val objective: Objective,
        val entries: MutableList<String>,
        var lastTitle: Component?,
        var lastLines: List<Component>,
        var task: ShimeTaskHandle?
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()

    override fun onInitialize(context: ShimeContext) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onShutdown() {
        HandlerList.unregisterAll(this)
        sessions.keys.toList().forEach { uuid -> Bukkit.getPlayer(uuid)?.let(::hide) }
        sessions.clear()
    }

    fun show(player: Player, sidebar: ShimeSidebar) {
        if (!scheduler.isPrimaryThread()) {
            scheduler.runSync { show(player, sidebar) }
            return
        }
        hide(player)
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, sidebar.titleProvider(player))
        objective.displaySlot = DisplaySlot.SIDEBAR
        player.scoreboard = scoreboard
        val session = Session(
            sidebar = sidebar,
            scoreboard = scoreboard,
            objective = objective,
            entries = ArrayList(),
            lastTitle = null,
            lastLines = emptyList(),
            task = null
        )
        sessions[player.uniqueId] = session
        update(player, session)
        session.task = scheduler.runRepeating(0L, sidebar.updateIntervalTicks) {
            update(player, session)
        }
    }

    fun hide(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        session.task?.cancel()
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }

    fun current(player: Player): ShimeSidebar? = sessions[player.uniqueId]?.sidebar

    fun refreshAll() {
        sessions.forEach { (uuid, session) ->
            Bukkit.getPlayer(uuid)?.let { update(it, session) }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        hide(event.player)
    }

    private fun update(player: Player, session: Session) {
        if (!player.isOnline) {
            hide(player)
            return
        }
        try {
            val title = session.sidebar.titleProvider(player)
            if (title != session.lastTitle) {
                session.objective.displayName(title)
                session.lastTitle = title
            }
            val rendered = session.sidebar.lineProvider(player).take(MAX_LINES)
            if (rendered == session.lastLines) {
                return
            }
            while (session.entries.size < rendered.size) {
                val index = session.entries.size
                val entry = entryFor(index)
                session.entries += entry
                session.objective.getScore(entry).score = MAX_LINES - index
            }
            while (session.entries.size > rendered.size) {
                val entry = session.entries.removeAt(session.entries.size - 1)
                session.scoreboard.resetScores(entry)
            }
            rendered.forEachIndexed { index, line ->
                val entry = session.entries[index]
                val teamName = "shime_line_$index"
                val team = session.scoreboard.getTeam(teamName)
                    ?: session.scoreboard.registerNewTeam(teamName)
                if (!team.hasEntry(entry)) {
                    team.addEntry(entry)
                }
                team.prefix(line)
            }
            session.lastLines = rendered
        } catch (throwable: Throwable) {
            logger.error(ShimeLogCategory.SCOREBOARD, "Could not update sidebar for ${player.name}.", throwable)
        }
    }

    private fun entryFor(index: Int): String {
        val color = "0123456789abcdef"[index % 16]
        return "\u00A7$color\u00A7r$index"
    }

    companion object {
        const val SERVICE_NAME = "scoreboard"
        private const val OBJECTIVE_NAME = "shime_sidebar"
        private const val MAX_LINES = 15
    }
}
