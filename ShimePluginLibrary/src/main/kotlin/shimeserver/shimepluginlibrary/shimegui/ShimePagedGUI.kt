package shimeserver.shimepluginlibrary.shimegui

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/** シンプルな前/次ページのナビゲーション行を持つ GUI。 */
abstract class ShimePagedGUI(
    id: String,
    title: net.kyori.adventure.text.Component,
    size: Int = 54
) : ShimeGUI(id, title, size) {
    private val pages = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Int>()

    val previousSlot: Int
        get() = size - 9

    val nextSlot: Int
        get() = size - 1

    fun currentPage(player: Player): Int = pages[player.uniqueId] ?: 0

    fun setPage(player: Player, page: Int): Boolean {
        val count = pageCount(player)
        if (page !in 0 until count) {
            return false
        }
        pages[player.uniqueId] = page
        return true
    }

    fun clearPage(player: Player) {
        pages.remove(player.uniqueId)
    }

    abstract fun pageCount(player: Player): Int

    protected abstract fun renderPage(player: Player, inventory: Inventory, page: Int, pageCount: Int)

    protected open fun previousButton(page: Int): org.bukkit.inventory.ItemStack? = null

    protected open fun nextButton(page: Int): org.bukkit.inventory.ItemStack? = null

    override fun render(player: Player, inventory: Inventory) {
        val count = pageCount(player).coerceAtLeast(1)
        val page = currentPage(player).coerceIn(0, count - 1)
        pages[player.uniqueId] = page
        renderPage(player, inventory, page, count)
        if (page > 0) {
            previousButton(page)?.let { inventory.setItem(previousSlot, it) }
        }
        if (page < count - 1) {
            nextButton(page)?.let { inventory.setItem(nextSlot, it) }
        }
    }

    override fun handleClick(
        player: Player,
        slot: Int,
        clickType: org.bukkit.event.inventory.ClickType,
        event: org.bukkit.event.inventory.InventoryClickEvent
    ): ShimeGUIAction {
        if (slot == previousSlot && currentPage(player) > 0) {
            pages[player.uniqueId] = currentPage(player) - 1
            return ShimeGUIAction.REFRESH
        }
        if (slot == nextSlot && currentPage(player) < pageCount(player) - 1) {
            pages[player.uniqueId] = currentPage(player) + 1
            return ShimeGUIAction.REFRESH
        }
        return ShimeGUIAction.NONE
    }

    override fun handleClose(player: Player) {
        clearPage(player)
    }
}
