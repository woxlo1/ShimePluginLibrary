package shimeserver.shimepluginlibrary.shimegui

import org.bukkit.Bukkit
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
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * GUI セッションを管理し、インベントリイベントを適切な [ShimeGUI] に振り分けます。
 *
 * アイテムの移動は既定でキャンセルされます。描画と更新の操作はプライマリスレッドから呼び出す必要があり、
 * 非同期のデータ読み込みは先にプライマリスレッドで完了させる必要があります。
 */
class ShimeGUIManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger,
    private val scheduler: ShimeScheduler
) : ShimeAbstractService(SERVICE_NAME), Listener {
    private data class Session(
        val gui: ShimeGUI,
        val inventory: Inventory,
        val holder: ShimeGUIHolder
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()

    override fun onInitialize(context: ShimeContext) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onShutdown() {
        HandlerList.unregisterAll(this)
        sessions.forEach { (uuid, session) ->
            Bukkit.getPlayer(uuid)?.let { player ->
                try {
                    session.gui.handleClose(player)
                } catch (throwable: Throwable) {
                    logger.warn(ShimeLogCategory.GUI, "GUI '${session.gui.id}' close handler failed on shutdown.", throwable)
                }
            }
        }
        sessions.clear()
    }

    fun open(player: Player, gui: ShimeGUI) {
        if (!scheduler.isPrimaryThread()) {
            scheduler.runSync { open(player, gui) }
            return
        }
        val holder = ShimeGUIHolder(gui)
        val inventory = Bukkit.createInventory(holder, gui.size, gui.title)
        holder.bind(inventory)
        val session = Session(gui, inventory, holder)
        sessions[player.uniqueId] = session
        if (gui.refreshOnOpen()) {
            gui.render(player, inventory)
        }
        player.openInventory(inventory)
    }

    fun close(player: Player) {
        sessions.remove(player.uniqueId)
        player.closeInventory()
    }

    fun current(player: Player): ShimeGUI? = sessions[player.uniqueId]?.gui

    fun isOpen(player: Player, guiId: String): Boolean = sessions[player.uniqueId]?.gui?.id == guiId

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

    fun updateSlot(player: Player, slot: Int, item: org.bukkit.inventory.ItemStack?) {
        val session = sessions[player.uniqueId] ?: return
        if (slot in 0 until session.inventory.size) {
            session.inventory.setItem(slot, item)
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
        try {
            session.gui.handleClose(player)
        } catch (throwable: Throwable) {
            logger.error(ShimeLogCategory.GUI, "GUI '${session.gui.id}' close handler failed for ${player.name}.", throwable)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val session = sessions.remove(event.player.uniqueId) ?: return
        try {
            session.gui.handleClose(event.player)
        } catch (throwable: Throwable) {
            logger.error(ShimeLogCategory.GUI, "GUI '${session.gui.id}' close handler failed on quit.", throwable)
        }
    }

    companion object {
        const val SERVICE_NAME = "gui"
    }
}
