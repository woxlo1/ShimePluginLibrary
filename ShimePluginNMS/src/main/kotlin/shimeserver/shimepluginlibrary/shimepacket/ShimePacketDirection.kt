package shimeserver.shimepluginlibrary.shimepacket

/** サーバーを基準としたパケットの方向。 */
enum class ShimePacketDirection {
    /** サーバーからクライアントへ送信される。 */
    SEND,

    /** クライアントからサーバーが受信する。 */
    RECEIVE
}
