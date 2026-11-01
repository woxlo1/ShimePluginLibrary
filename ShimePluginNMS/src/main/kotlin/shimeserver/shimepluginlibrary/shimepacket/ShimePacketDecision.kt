package shimeserver.shimepluginlibrary.shimepacket

/** パケットフィルターが返す結果。 */
enum class ShimePacketDecision {
    /** 処理を続行し、パケットをリスナーへ配信する。 */
    ALLOW,

    /** 処理を停止し、パケットを破棄する。 */
    DENY
}
