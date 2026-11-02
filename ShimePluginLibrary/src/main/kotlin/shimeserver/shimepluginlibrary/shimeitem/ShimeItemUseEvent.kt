package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

/** カスタムアイテムが検出したクリック種別。 */
enum class ShimeItemClick {
    LEFT,
    RIGHT
}

/**
 * カスタムアイテムのハンドラーが呼ばれる直前に発火するイベント。
 *
 * キャンセルするとハンドラーは実行されず、元の [org.bukkit.event.player.PlayerInteractEvent] もキャンセルされます。
 * クールダウン中かどうかはこのイベントの後に判定されるため、リスナーはクールダウンに依存せずに処理を拒否できます。
 */
class ShimeItemUseEvent(
    val player: Player,
    val item: ItemStack,
    val definition: ShimeItemDefinition,
    val click: ShimeItemClick
) : Event(), Cancellable {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
