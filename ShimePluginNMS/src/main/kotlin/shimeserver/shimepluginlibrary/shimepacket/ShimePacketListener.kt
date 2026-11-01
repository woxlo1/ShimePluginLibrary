package shimeserver.shimepluginlibrary.shimepacket

/**
 * 登録されたすべてのフィルターを通過したパケットを受け取る。
 *
 * リスナーは登録順に呼び出される。あるリスナーからの例外はログに記録され、残りのリスナーの処理を
 * 止めることはない。
 */
fun interface ShimePacketListener {
    /** 1 つのパケットイベントに対して呼び出される。 */
    fun onPacket(context: ShimePacketContext)
}
