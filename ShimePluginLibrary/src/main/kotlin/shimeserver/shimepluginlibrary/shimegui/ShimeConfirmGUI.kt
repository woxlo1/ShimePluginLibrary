package shimeserver.shimepluginlibrary.shimegui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeitem.ShimeItemBuilder

/** 再利用可能なはい/いいえの確認 GUI。 */
class ShimeConfirmGUI(
    plugin: JavaPlugin,
    id: String,
    title: Component,
    private val confirmText: Component,
    private val cancelText: Component,
    private val onConfirm: (Player) -> Unit,
    private val onCancel: (Player) -> Unit = {}
) : ShimeGUI(id, title, 27) {
    private val confirmItem = ShimeItemBuilder(plugin, Material.LIME_CONCRETE)
        .name(confirmText)
        .build()
    private val cancelItem = ShimeItemBuilder(plugin, Material.RED_CONCRETE)
        .name(cancelText)
        .build()

    override fun render(player: Player, inventory: Inventory) {
        inventory.setItem(CONFIRM_SLOT, confirmItem)
        inventory.setItem(CANCEL_SLOT, cancelItem)
    }

    override fun handleClick(
        player: Player,
        slot: Int,
        clickType: ClickType,
        event: InventoryClickEvent
    ): ShimeGUIAction {
        return when (slot) {
            CONFIRM_SLOT -> {
                onConfirm(player)
                ShimeGUIAction.CLOSE
            }
            CANCEL_SLOT -> {
                onCancel(player)
                ShimeGUIAction.CLOSE
            }
            else -> ShimeGUIAction.NONE
        }
    }

    companion object {
        const val CONFIRM_SLOT = 11
        const val CANCEL_SLOT = 15
    }
}
