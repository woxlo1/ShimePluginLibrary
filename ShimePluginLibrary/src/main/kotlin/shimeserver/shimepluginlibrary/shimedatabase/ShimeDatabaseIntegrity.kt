package shimeserver.shimepluginlibrary.shimedatabase

/**
 * データベース整合性チェック 1 件分の結果。
 *
 * [details] には失敗理由だけを格納し、接続文字列やバインド値などの機密情報は含めません。
 */
data class ShimeDatabaseIntegrityCheck(
    val name: String,
    val passed: Boolean,
    val details: String? = null
) {
    init {
        require(name.isNotBlank()) { "Integrity check name must not be blank." }
    }
}

/**
 * データベース 1 件分の整合性レポート。
 *
 * チェックは接続確認、指定テーブルの存在確認、SQLite の場合は `PRAGMA integrity_check` と
 * `PRAGMA foreign_key_check` の結果で構成されます。すべてのチェックが成功した場合のみ
 * [isHealthy] が true になります。
 */
data class ShimeDatabaseIntegrityReport(
    val databaseName: String,
    val checks: List<ShimeDatabaseIntegrityCheck>
) {
    init {
        require(databaseName.isNotBlank()) { "Database name must not be blank." }
    }

    val isHealthy: Boolean
        get() = checks.isNotEmpty() && checks.all { it.passed }

    /** 失敗したチェックのみを返す。 */
    fun failedChecks(): List<ShimeDatabaseIntegrityCheck> = checks.filterNot { it.passed }

    /** 人が読める形の要約。 */
    fun summary(): String {
        if (isHealthy) {
            return "Database '$databaseName' passed all ${checks.size} integrity check(s)."
        }
        return failedChecks().joinToString("; ") { check ->
            "${check.name}: ${check.details ?: "failed"}"
        }
    }
}
