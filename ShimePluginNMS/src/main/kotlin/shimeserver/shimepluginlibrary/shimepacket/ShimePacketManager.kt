package shimeserver.shimepluginlibrary.shimepacket

import org.bukkit.entity.Player
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimenms.ShimeNmsUnsupportedException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * パケットフィルターとリスナー、およびパケット送信に使用するトランスポートのためのスレッドセーフなレジストリ。
 *
 * このマネージャーは [shimeserver.shimepluginlibrary.shimenms.ShimeNmsManager] が所有する。意図的に
 * 組み込みのトランスポートを持たない。パケットサポートを検証済みのアダプターがない場合、[send] は
 * パケットを黙って破棄するのではなく [ShimeNmsUnsupportedException] を投げる。
 *
 * フィルターはリスナーの前に、優先度の降順で実行される。フィルターの例外はログに記録され、
 * [ShimePacketDecision.DENY] として扱われる。これにより、壊れたフィルターが誤ってセキュリティチェックを
 * 迂回することはない。
 */
class ShimePacketManager(private val logger: ShimeLogger) : AutoCloseable {
    private data class FilterEntry(val id: Long, val priority: Int, val filter: ShimePacketFilter)
    private data class ListenerEntry(val id: Long, val listener: ShimePacketListener)

    private val ids = AtomicLong()
    private val filters = CopyOnWriteArrayList<FilterEntry>()
    private val listeners = CopyOnWriteArrayList<ListenerEntry>()

    @Volatile
    private var transport: ShimePacketTransport? = null

    /** パケットトランスポートを置き換える。アダプターのシャットダウン時は `null` を渡す。 */
    fun setTransport(transport: ShimePacketTransport?) {
        this.transport = transport
        logger.debug(
            ShimeLogCategory.PACKET,
            if (transport == null) {
                "Packet transport cleared."
            } else {
                "Packet transport installed (send=${transport.supportsSending}, receive=${transport.supportsReceiving})."
            }
        )
    }

    /** インストールされているトランスポートがあれば返す。 */
    fun transport(): ShimePacketTransport? = transport

    /** トランスポートがインストールされているかどうかを返す。 */
    fun hasTransport(): Boolean = transport != null

    /** インストールされているトランスポートがパケットを送信できるかどうかを返す。 */
    fun supportsSending(): Boolean = transport?.supportsSending == true

    /** インストールされているトランスポートがパケットを受信できるかどうかを返す。 */
    fun supportsReceiving(): Boolean = transport?.supportsReceiving == true

    /**
     * フィルターを登録する。
     *
     * @param priority 値が大きいほど先に実行される。同じ優先度の場合は登録順が維持される。
     */
    @JvmOverloads
    fun addFilter(filter: ShimePacketFilter, priority: Int = 0): ShimePacketRegistration {
        val entry = FilterEntry(ids.incrementAndGet(), priority, filter)
        filters += entry
        return ShimePacketRegistration { filters.remove(entry) }
    }

    /** すべてのフィルターがパケットを許可した後に実行されるリスナーを登録する。 */
    fun addListener(listener: ShimePacketListener): ShimePacketRegistration {
        val entry = ListenerEntry(ids.incrementAndGet(), listener)
        listeners += entry
        return ShimePacketRegistration { listeners.remove(entry) }
    }

    /** 登録済みフィルターの数を返す。 */
    fun filterCount(): Int = filters.size

    /** 登録済みリスナーの数を返す。 */
    fun listenerCount(): Int = listeners.size

    /**
     * インストールされているトランスポートを通じて [player] へ [packet] を送信する。
     *
     * @throws ShimeNmsUnsupportedException トランスポートがインストールされていないか、送信できない場合。
     * @throws IllegalArgumentException [packet] が送信用のパケットでない場合。
     */
    fun send(player: Player, packet: ShimePacket) {
        require(packet.direction == ShimePacketDirection.SEND) {
            "Packet '${packet.name}' is not an outgoing packet."
        }
        val current = transport ?: throw ShimeNmsUnsupportedException(
            "No packet transport is installed. Install an NMS adapter that supports packet sending."
        )
        if (!current.supportsSending) {
            throw ShimeNmsUnsupportedException(
                "The installed packet transport does not support sending packet '${packet.name}'."
            )
        }
        current.send(player, packet)
    }

    /**
     * 受信したパケットをフィルターとリスナーへ配信する。
     *
     * このメソッドはクライアントのパケットをインターセプトするアダプター向けである。フィルターがパケットを
     * 拒否した場合は [ShimePacketDecision.DENY] を返すため、呼び出し元はネイティブパケットを破棄しなければならない。
     */
    fun dispatchReceived(player: Player, packet: ShimePacket): ShimePacketDecision {
        require(packet.direction == ShimePacketDirection.RECEIVE) {
            "Packet '${packet.name}' is not an incoming packet."
        }
        val context = ShimePacketContext(
            player = player,
            packet = packet,
            direction = ShimePacketDirection.RECEIVE,
            sequence = ShimePacketContext.nextSequence()
        )
        val orderedFilters = filters.sortedWith(
            compareByDescending<FilterEntry> { it.priority }.thenBy { it.id }
        )
        for (entry in orderedFilters) {
            val decision = try {
                entry.filter.filter(context)
            } catch (throwable: Throwable) {
                logger.error(
                    ShimeLogCategory.PACKET,
                    "Packet filter for '${packet.name}' failed; the packet is denied.",
                    throwable
                )
                ShimePacketDecision.DENY
            }
            if (decision == ShimePacketDecision.DENY) {
                logger.debug(ShimeLogCategory.PACKET, "Packet '${packet.name}' was denied by a filter.")
                return ShimePacketDecision.DENY
            }
        }
        for (entry in listeners) {
            try {
                entry.listener.onPacket(context)
            } catch (throwable: Throwable) {
                logger.error(
                    ShimeLogCategory.PACKET,
                    "Packet listener for '${packet.name}' failed; remaining listeners still run.",
                    throwable
                )
            }
        }
        return ShimePacketDecision.ALLOW
    }

    /** すべてのフィルターとリスナーを削除し、トランスポートをクリアする。 */
    fun clear() {
        filters.clear()
        listeners.clear()
        transport = null
    }

    /** すべてのパケット状態を解放する。複数回呼び出しても安全である。 */
    override fun close() {
        clear()
    }
}
