package shimeserver.shimepluginlibrary.shimeconfig

/** 設定ファイル内の 1 つの検証問題。 */
data class ShimeConfigIssue(
    val path: String,
    val message: String,
    val severity: ShimeConfigSeverity
) {
    init {
        require(path.isNotBlank()) { "Issue path must not be blank." }
        require(message.isNotBlank()) { "Issue message must not be blank." }
    }

    override fun toString(): String = "[$severity] $path: $message"
}
