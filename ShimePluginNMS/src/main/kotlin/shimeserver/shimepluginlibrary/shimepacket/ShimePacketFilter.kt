package shimeserver.shimepluginlibrary.shimepacket

/**
 * リスナーがパケットを受け取る前にパケットを検査する。
 *
 * フィルターはパケットを配信するスレッド上で呼び出される。実装は長時間ブロックしてはならず、
 * 無限に再帰する可能性のある方法でパケットマネージャーを呼び戻してもならない。
 */
fun interface ShimePacketFilter {
    /** パケットを破棄するには [ShimePacketDecision.DENY] を返す。 */
    fun filter(context: ShimePacketContext): ShimePacketDecision
}
