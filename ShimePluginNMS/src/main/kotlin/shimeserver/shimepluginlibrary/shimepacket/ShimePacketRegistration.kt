package shimeserver.shimepluginlibrary.shimepacket

/**
 * フィルターまたはリスナーの登録時に返されるハンドル。
 *
 * ハンドルのクローズは冪等であり、それが表す登録のみを削除する。
 */
class ShimePacketRegistration internal constructor(private val onClose: () -> Unit) : AutoCloseable {
    @Volatile
    private var active = true

    /** この登録がまだ有効かどうかを返す。 */
    val isActive: Boolean
        get() = active

    /** 登録を削除する。複数回呼び出しても安全である。 */
    override fun close() {
        if (active) {
            active = false
            onClose()
        }
    }
}
