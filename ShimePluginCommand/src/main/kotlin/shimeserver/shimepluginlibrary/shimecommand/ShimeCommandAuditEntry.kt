package shimeserver.shimepluginlibrary.shimecommand

/**
 * コマンド実行の監査ログ 1 件分。
 *
 * [arguments] には送信者が入力した引数がそのまま含まれます。パスワードなどの機密情報をコマンド引数として
 * 受け取る設計にしないでください。監査ログはメモリ上にのみ保持され、上限を超えると古いものから破棄されます。
 */
data class ShimeCommandAuditEntry(
    val timestampMillis: Long,
    val senderName: String,
    val senderId: String,
    val commandPath: String,
    val label: String,
    val arguments: List<String>,
    val resultType: String,
    val success: Boolean,
    val durationNanos: Long
) {
    /** 実行にかかった時間のミリ秒表現。診断用。 */
    val durationMillis: Long
        get() = durationNanos / 1_000_000L
}
