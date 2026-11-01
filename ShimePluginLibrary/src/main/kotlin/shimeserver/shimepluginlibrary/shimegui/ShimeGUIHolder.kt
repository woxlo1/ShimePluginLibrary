package shimeserver.shimepluginlibrary.shimegui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/** タイトルに依存せずにマネージャーが自身の GUI を識別できるようにするインベントリホルダー。 */
class ShimeGUIHolder internal constructor(
    internal val gui: ShimeGUI
) : InventoryHolder {
    private lateinit var inventory: Inventory

    internal fun bind(inventory: Inventory) {
        this.inventory = inventory
    }

    override fun getInventory(): Inventory = inventory
}
