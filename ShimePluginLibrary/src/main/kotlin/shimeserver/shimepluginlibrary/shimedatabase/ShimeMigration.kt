package shimeserver.shimepluginlibrary.shimedatabase

/**
 * 単一のスキーマ移行。
 *
 * 対象データベースが DDL の暗黙コミットを行う場合、各ステートメントは冪等でなければならない。
 */
data class ShimeMigration(
    val version: Int,
    val description: String,
    val statements: List<String>
) {
    init {
        require(version > 0) { "Migration version must be positive." }
        require(description.isNotBlank()) { "Migration description must not be blank." }
        require(statements.isNotEmpty()) { "Migration must contain at least one statement." }
    }
}
