package shimeserver.shimepluginlibrary.shimegui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

/**
 * チェスト GUI の基底クラス。
 *
 * 描画は常にプライマリスレッドで行われます。マネージャーは既定でアイテムの移動をキャンセルします。
 * 意図的に編集可能なインベントリとして動作させる GUI に限り、[allowItemPickup] をオーバーライドしてください。
 */
abstract class ShimeGUI(
    val id: String,
    val title: Component,
    val size: Int
) {
    init {
        require(id.isNotBlank()) { "GUI id must not be blank." }
        require(size in 9..54 && size % 9 == 0) { "GUI size must be a multiple of 9 between 9 and 54." }
    }

    open fun render(player: Player, inventory: Inventory) {
        // サブクラスはここにアイテムを配置します。
    }

    open fun handleClick(
        player: Player,
        slot: Int,
        clickType: ClickType,
        event: InventoryClickEvent
    ): ShimeGUIAction = ShimeGUIAction.NONE

    open fun handleClose(player: Player) {
        // サブクラスはここでセッションごとの状態を解放します。
    }

    open fun allowItemPickup(): Boolean = false

    open fun allowDrag(): Boolean = false

    open fun refreshOnOpen(): Boolean = true
}
