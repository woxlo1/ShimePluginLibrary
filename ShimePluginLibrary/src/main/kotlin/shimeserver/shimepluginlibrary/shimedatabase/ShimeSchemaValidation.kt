package shimeserver.shimepluginlibrary.shimedatabase

import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation

/**
 * スキーマ検証で期待するテーブル 1 件分の定義。
 *
 * テーブル名と列名は SQL に埋め込まれるため、[ShimeValidation.requireSafeKey] で許可された
 * 文字種だけを受け付けます。値そのものは扱わないため、ここに機密情報を含めることはありません。
 */
class ShimeTableSpec(
    val table: String,
    val columns: Set<String>
) {
    init {
        ShimeValidation.requireSafeKey(table, "schema table")
        require(columns.isNotEmpty()) { "Table '$table' must declare at least one column." }
        columns.forEach { ShimeValidation.requireSafeKey(it, "schema column of '$table'") }
    }

    /** 診断用の文字列表現。 */
    override fun toString(): String {
        return "ShimeTableSpec(table='$table', columns=${columns.sorted()})"
    }
}

/** スキーマ検証で見つかった問題の種別。 */
enum class ShimeSchemaIssueType {
    /** テーブル自体が存在しない。 */
    MISSING_TABLE,

    /** テーブルは存在するが、期待する列がない。 */
    MISSING_COLUMN,

    /** テーブルを読み取れなかった。権限不足や接続断などが原因になり得る。 */
    UNREADABLE_TABLE
}

/** スキーマ検証で見つかった問題 1 件。 */
data class ShimeSchemaIssue(
    val type: ShimeSchemaIssueType,
    val table: String,
    val column: String? = null,
    val message: String
) {
    init {
        require(table.isNotBlank()) { "Schema issue table must not be blank." }
        require(message.isNotBlank()) { "Schema issue message must not be blank." }
    }
}

/**
 * スキーマ検証の結果。
 *
 * [issues] が空であることと [isValid] が true であることは同義です。問題はテーブル単位で集約され、
 * 列の不足は 1 列につき 1 件として記録されます。
 */
data class ShimeSchemaValidationReport(
    val databaseName: String,
    val checkedTables: List<String>,
    val issues: List<ShimeSchemaIssue>
) {
    init {
        require(databaseName.isNotBlank()) { "Database name must not be blank." }
    }

    val isValid: Boolean
        get() = issues.isEmpty()

    /** 問題が見つかったテーブル名の集合。 */
    fun invalidTables(): Set<String> = issues.map { it.table }.toSet()

    /** 人が読める形の要約。 */
    fun summary(): String {
        if (isValid) {
            return "Database '$databaseName' matches the expected schema of ${checkedTables.size} table(s)."
        }
        return issues.joinToString("; ") { it.message }
    }
}
