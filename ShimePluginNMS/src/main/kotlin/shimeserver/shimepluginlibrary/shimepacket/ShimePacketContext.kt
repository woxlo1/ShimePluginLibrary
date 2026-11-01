package shimeserver.shimepluginlibrary.shimepacket

import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicLong

/**
 * 1 つのパケットイベントに対する不変のコンテキスト。
 *
 * [sequence] は [ShimePacketManager] によって割り当てられる単調増加の識別子である。これにより、
 * フィルターは実時間のタイムスタンプに依存せずに、パケットとログ行を関連付けられる。
 */
class ShimePacketContext(
    val player: Player,
    val packet: ShimePacket,
    val direction: ShimePacketDirection,
    val sequence: Long,
    val receivedAtMillis: Long = System.currentTimeMillis()
) {
    /** パケットがサーバーからクライアントへ向かっているかどうかを返す。 */
    val isOutgoing: Boolean
        get() = direction == ShimePacketDirection.SEND

    /** パケットがクライアントからサーバーへ向かっているかどうかを返す。 */
    val isIncoming: Boolean
        get() = direction == ShimePacketDirection.RECEIVE

    companion object {
        private val counter = AtomicLong()

        /** 次のシーケンス番号を返す。独自のコンテキストを構築するアダプター向けに公開されている。 */
        @JvmStatic
        fun nextSequence(): Long = counter.incrementAndGet()
    }
}
