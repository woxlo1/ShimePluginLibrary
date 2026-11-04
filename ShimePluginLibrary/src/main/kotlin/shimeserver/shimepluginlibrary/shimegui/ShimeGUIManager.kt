package shimeserver.shimepluginlibrary.shimegui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimescheduler.ShimeTaskHandle
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * GUI セッションを管理し、インベントリイベントを適切な [ShimeGUI] に振り分けます。
 *
 * アイテムの移動は既定でキャンセルされます。描画と更新の操作はプライマリスレッドから呼び出す必要があり、
 * 非同期のデータ読み込みは [refreshAfter] または [updateAfter] を使ってプライマリスレッドへ戻してください。
 * [scheduleRefresh] で定期的な再描画を登録でき、セッション終了時に自動で解除されます。
 */
class ShimeGUIManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger,
    private val scheduler: ShimeScheduler
) : ShimeAbstractService(SERVICE_NAME), Listener {
    private data class Session(
        val gui: ShimeGUI,
        val inventory: Inventory,
        val holder: ShimeGUIHolder,
        var refreshTask: ShimeTaskHandle? = null
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()
    private val history = ConcurrentHashMap<UUID, MutableList<String>>()

    override fun onInitialize(context: ShimeContext) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onShutdown() {
        HandlerList.unregisterAll(this)
        sessions.forEach { (uuid, session) ->
            session.refreshTask?.cancel()
            Bukkit.getPlayer(uuid)?.let { player ->
                try {
                    session.gui.handleClose(player)
                } catch (throwable: Throwable) {
                    logger.warn(ShimeLogCategory.GUI, "GUI '${session.gui.id}' close handler failed on shutdown.", throwable)
                }
            }
        }
        sessions.clear()
        history.clear()
    }

    /**
     * [player] に [gui] を開きます。
     *
     * GUI のアクセス制御に失敗した場合は開かず、[deniedMessage] を送信します。非同期スレッドから呼ばれた場合は
     * プライマリスレッドへ委譲します。
     */
    @JvmOverloads
    fun open(
        player: Player,
        gui: ShimeGUI,
        deniedMessage: Component = Component.text("You do not have access to this menu.")
    ) {
        if (!scheduler.isPrimaryThread()) {
            scheduler.runSync { open(player, gui, deniedMessage) }
            return
        }
        if (!gui.hasAccess(player)) {
            player.sendMessage(deniedMessage)
            return
        }
        sessions.remove(player.uniqueId)?.refreshTask?.cancel()
        val holder = ShimeGUIHolder(gui)
        val inventory = Bukkit.createInventory(holder, gui.size, gui.title)
        holder.bind(inventory)
        val session = Session(gui, inventory, holder)
        sessions[player.uniqueId] = session
        history.computeIfAbsent(player.uniqueId) { CopyOnWriteArrayList() }.add(gui.id)
        if (gui.refreshOnOpen()) {
            gui.render(player, inventory)
        }
        player.openInventory(inventory)
    }

    fun close(player: Player) {
        sessions.remove(player.uniqueId)?.refreshTask?.cancel()
        player.closeInventory()
    }

    fun current(player: Player): ShimeGUI? = sessions[player.uniqueId]?.gui

    fun isOpen(player: Player, guiId: String): Boolean = sessions[player.uniqueId]?.gui?.id == guiId

    /** [player] のセッションで開いた GUI の履歴を古い順に返す。 */
    fun history(player: Player): List<String> = history[player.uniqueId]?.toList() ?: emptyList()

    /** [player] の GUI 履歴を消去する。 */
    fun clearHistory(player: Player) {
        history.remove(player.uniqueId)
    }

    fun refresh(player: Player): Boolean {
        if (!scheduler.isPrimaryThread()) {
            scheduler.runSync { refresh(player) }
            return true
        }
        val session = sessions[player.uniqueId] ?: return false
        session.inventory.clear()
        session.gui.render(player, session.inventory)
        player.updateInventory()
        return true
    }

    fun refreshAll(guiId: String) {
        sessions.entries.filter { it.value.gui.id == guiId }.forEach { (uuid, _) ->
            val player = plugin.server.getPlayer(uuid) ?: return@forEach
            refresh(player)
        }
    }

    /**
     * [intervalTicks] ごとに [player] の現在の GUI を再描画します。
     *
     * 既存の定期更新は置き換えられます。[intervalTicks] が 0 以下の場合は定期更新を解除します。
     */
    fun scheduleRefresh(player: Player, intervalTicks: Long) {
        if (!scheduler.isPrimaryThread()) {
            scheduler.runSync { scheduleRefresh(player, intervalTicks) }
            return
        }
        val session = sessions[player.uniqueId] ?: return
        session.refreshTask?.cancel()
        session.refreshTask = if (intervalTicks <= 0L) {
            null
        } else {
            scheduler.runRepeating(intervalTicks, intervalTicks) {
                val current = sessions[player.uniqueId] ?: return@runRepeating
                if (current.gui !== session.gui) {
                    return@runRepeating
                }
                if (player.isOnline && player.openInventory.topInventory === current.inventory) {
                    current.inventory.clear()
                    current.gui.render(player, current.inventory)
                    player.updateInventory()
                }
            }
        }
    }

    /** [player] の定期更新を解除する。 */
    fun cancelScheduledRefresh(player: Player) {
        sessions[player.uniqueId]?.refreshTask?.cancel()
        sessions[player.uniqueId]?.refreshTask = null
    }

    /**
     * [loader] を非同期で実行し、完了後にプライマリスレッドで GUI を再描画します。
     *
     * [loader] から Bukkit API を直接操作してはいけません。結果は [onLoaded] に渡されます。
     */
    fun <T> refreshAfter(player: Player, loader: () -> T, onLoaded: (T) -> Unit = {}) {
        scheduler.runAsync {
            val value = try {
                loader()
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.GUI, "Asynchronous GUI loader failed for ${player.name}.", throwable)
                return@runAsync
            }
            scheduler.runSync {
                if (!player.isOnline) {
                    return@runSync
                }
                try {
                    onLoaded(value)
                } catch (throwable: Throwable) {
                    logger.error(ShimeLogCategory.GUI, "GUI loader callback failed for ${player.name}.", throwable)
                    return@runSync
                }
                refresh(player)
            }
        }
    }

    /**
     * [loader] を非同期で実行し、完了後にプライマリスレッドで [onLoaded] を呼びます。
     *
     * GUI を再描画しない点だけが [refreshAfter] と異なります。
     */
    fun <T> updateAfter(player: Player, loader: () -> T, onLoaded: (T) -> Unit) {
        scheduler.runAsync {
            val value = try {
                loader()
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.GUI, "Asynchronous GUI loader failed for ${player.name}.", throwable)
                return@runAsync
            }
            scheduler.runSync {
                if (!player.isOnline) {
                    return@runSync
                }
                try {
                    onLoaded(value)
                } catch (throwable: Throwable) {
                    logger.error(ShimeLogCategory.GUI, "GUI loader callback failed for ${player.name}.", throwable)
                }
            }
        }
    }

    fun updateSlot(player: Player, slot: Int, item: org.bukkit.inventory.ItemStack?) {
        if (!scheduler.isPrimaryThread()) {
            scheduler.runSync { updateSlot(player, slot, item) }
            return
        }
        val session = sessions[player.uniqueId] ?: return
        if (slot in 0 until session.inventory.size) {
            session.inventory.setItem(slot, item)
            player.updateInventory()
        }
    }

    fun sessions(): Int = sessions.size

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ShimeGUIHolder ?: return
        val player = event.whoClicked as? Player ?: return
        val session = sessions[player.uniqueId] ?: return
        if (session.inventory !== event.view.topInventory) {
            return
        }
        val gui = holder.gui
        if (!gui.allowItemPickup()) {
            event.isCancelled = true
        }
        if (event.clickedInventory === session.inventory) {
            playClickFeedback(gui, player, event.slot, event.click)
            val action = try {
                gui.handleClick(player, event.slot, event.click, event)
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.GUI, "GUI '${gui.id}' click handler failed for ${player.name}.", throwable)
                ShimeGUIAction.NONE
            }
            when (action) {
                ShimeGUIAction.REFRESH -> refresh(player)
                ShimeGUIAction.CLOSE -> close(player)
                ShimeGUIAction.NONE -> Unit
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? ShimeGUIHolder ?: return
        if (!holder.gui.allowDrag()) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val session = sessions[player.uniqueId] ?: return
        if (session.inventory !== event.inventory) {
            return
        }
        sessions.remove(player.uniqueId, session)
        session.refreshTask?.cancel()
        try {
            session.gui.handleClose(player)
        } catch (throwable: Throwable) {
            logger.error(ShimeLogCategory.GUI, "GUI '${session.gui.id}' close handler failed for ${player.name}.", throwable)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val session = sessions.remove(event.player.uniqueId) ?: return
        session.refreshTask?.cancel()
        try {
            session.gui.handleClose(event.player)
        } catch (throwable: Throwable) {
            logger.error(ShimeLogCategory.GUI, "GUI '${session.gui.id}' close handler failed on quit.", throwable)
        }
        history.remove(event.player.uniqueId)
    }

    private fun playClickFeedback(gui: ShimeGUI, player: Player, slot: Int, clickType: org.bukkit.event.inventory.ClickType) {
        try {
            gui.clickSound()?.let { sound -> player.playSound(player.location, sound, 0.8f, 1.0f) }
            gui.clickMessage(player, slot, clickType)?.let(player::sendMessage)
        } catch (throwable: Throwable) {
            logger.warn(ShimeLogCategory.GUI, "GUI '${gui.id}' click feedback failed for ${player.name}.", throwable)
        }
    }

    companion object {
        const val SERVICE_NAME = "gui"
    }
}
