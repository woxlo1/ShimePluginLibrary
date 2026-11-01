package shimeserver.shimepluginlibrary.shimepacket

import org.bukkit.entity.Player

/**
 * サーバー内部を必要とするパケット向けの、アダプターが所有するトランスポート。
 *
 * Paper の公開 API は生のパケット送信を公開していないため、ライブラリはトランスポートなしで提供される。
 * 外部の NMS アダプターは、サポートするサーバーバージョンを検証した後にトランスポートを提供できる。
 * トランスポートが登録されていない場合、[ShimePacketManager.send] は送信したふりをせずに明示的に失敗する。
 */
interface ShimePacketTransport {
    /** [send] が検出されたサーバーバージョン向けに実装されているかどうかを返す。 */
    val supportsSending: Boolean

    /** 受信したパケットを [ShimePacketManager.dispatchReceived] へ配信できるかどうかを返す。 */
    val supportsReceiving: Boolean

    /**
     * [player] へ [packet] を送信する。
     *
     * @throws shimeserver.shimepluginlibrary.shimenms.ShimeNmsUnsupportedException このトランスポートが
     * 送信をサポートしていない場合。
     */
    fun send(player: Player, packet: ShimePacket)
}
