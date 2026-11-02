package shimeserver.shimepluginlibrary.shimeconfig

/**
 * 設定値の変更を表す不変の通知。
 *
 * [oldValue] が null で [newValue] が非 null の場合は追加、逆の場合は削除です。値そのものはログへ
 * 出力しないでください。パスワードなどの機密情報が含まれる可能性があります。
 */
data class ShimeConfigChange(
    val configName: String,
    val path: String,
    val oldValue: Any?,
    val newValue: Any?
) {
    val isAddition: Boolean
        get() = oldValue == null && newValue != null

    val isRemoval: Boolean
        get() = oldValue != null && newValue == null
}

/** 設定値の変更を受け取るリスナー。 */
fun interface ShimeConfigListener {
    fun onChange(change: ShimeConfigChange)
}

/** [ShimeConfig.addListener] が返す購読ハンドル。二重に解除しても安全です。 */
class ShimeConfigSubscription internal constructor(
    private val onCancel: () -> Unit
) : AutoCloseable {
    @Volatile
    private var active = true

    val isActive: Boolean
        get() = active

    fun cancel() {
        if (active) {
            active = false
            onCancel()
        }
    }

    override fun close() = cancel()
}
