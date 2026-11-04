package shimeserver.shimepluginlibrary.shimedatabase

import shimeserver.shimepluginlibrary.shimeexception.ShimeDatabaseException
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation

/**
 * 順序付けられたスキーマ移行を適用し、そのバージョンを記録する。
 *
 * このマネージャーはプラグイン開発者が提供した SQL のみを実行し、プレイヤーや設定ファイルからの SQL を
 * 受け付けることはない。該当する場合、値は引き続き呼び出し側でバインドされることが想定されている。
 *
 * 移行に加えて、期待するテーブルと列の存在を確認する [validate] と、接続・テーブル・エンジン固有の
 * 整合性を確認する [integrityCheck] を提供します。
 */
class ShimeSchemaManager(
    private val database: ShimeDatabase,
    private val historyTable: String = "shime_schema_history"
) {
    init {
        ShimeValidation.requireSafeKey(historyTable, "history table")
    }

    /**
     * 未適用の移行をバージョン順に適用する。
     *
     * 各移行は独立したトランザクションで実行され、途中で失敗した場合はその移行だけがロールバックされます。
     * すでに適用済みのバージョンはスキップされます。
     */
    fun migrate(migrations: List<ShimeMigration>) {
        if (migrations.isEmpty()) {
            return
        }
        ensureHistoryTable()
        val applied = appliedVersions()
        val pending = migrations.sortedBy { it.version }.filter { it.version !in applied }
        for (migration in pending) {
            database.transaction { connection ->
                for (statement in migration.statements) {
                    connection.createStatement().use { sql ->
                        sql.queryTimeout = 30
                        sql.execute(statement)
                    }
                }
                connection.prepareStatement(
                    "INSERT INTO $historyTable (version, description, applied_at) VALUES (?, ?, ?)"
                ).use { insert ->
                    insert.setInt(1, migration.version)
                    insert.setString(2, migration.description)
                    insert.setLong(3, System.currentTimeMillis())
                    insert.executeUpdate()
                }
            }
        }
    }

    /** 適用済みの移行バージョン。 */
    fun appliedVersions(): Set<Int> {
        ensureHistoryTable()
        return database.query("SELECT version FROM $historyTable") { resultSet ->
            resultSet.getInt("version")
        }.toSet()
    }

    /**
     * テーブルが存在し、読み取り可能かを返す。
     *
     * 権限不足などで読み取れない場合も false になります。存在と読み取り可否を区別したい場合は
     * [validate] を使用してください。
     */
    fun tableExists(table: String): Boolean {
        ShimeValidation.requireSafeKey(table, "table")
        return try {
            database.query("SELECT 1 FROM $table WHERE 1 = 0") { 1 }
            true
        } catch (_: ShimeDatabaseException) {
            false
        }
    }

    /**
     * 期待するスキーマと実際のデータベースを比較する。
     *
     * テーブルと列の照合は大文字小文字を区別しませんが、テーブル名・列名は [ShimeTableSpec] の
     * 検証済み識別子のみを扱います。データの内容には触れません。
     */
    fun validate(specs: List<ShimeTableSpec>): ShimeSchemaValidationReport {
        if (specs.isEmpty()) {
            return ShimeSchemaValidationReport(database.name, emptyList(), emptyList())
        }
        val issues = ArrayList<ShimeSchemaIssue>()
        val existingTables = try {
            tableNames()
        } catch (exception: ShimeDatabaseException) {
            specs.forEach { spec ->
                issues += ShimeSchemaIssue(
                    ShimeSchemaIssueType.UNREADABLE_TABLE,
                    spec.table,
                    null,
                    "Could not read table metadata for '${spec.table}': ${exception.message}"
                )
            }
            return ShimeSchemaValidationReport(database.name, specs.map { it.table }, issues)
        }
        for (spec in specs) {
            val actualTable = existingTables.firstOrNull { it.equals(spec.table, ignoreCase = true) }
            if (actualTable == null) {
                issues += ShimeSchemaIssue(
                    ShimeSchemaIssueType.MISSING_TABLE,
                    spec.table,
                    null,
                    "Table '${spec.table}' does not exist."
                )
                continue
            }
            val actualColumns = try {
                columnNames(actualTable)
            } catch (exception: ShimeDatabaseException) {
                issues += ShimeSchemaIssue(
                    ShimeSchemaIssueType.UNREADABLE_TABLE,
                    spec.table,
                    null,
                    "Could not read columns of '${spec.table}': ${exception.message}"
                )
                continue
            }
            val normalized = actualColumns.map { it.lowercase() }.toSet()
            spec.columns.forEach { expected ->
                if (expected.lowercase() !in normalized) {
                    issues += ShimeSchemaIssue(
                        ShimeSchemaIssueType.MISSING_COLUMN,
                        spec.table,
                        expected,
                        "Table '${spec.table}' is missing column '$expected'."
                    )
                }
            }
        }
        return ShimeSchemaValidationReport(database.name, specs.map { it.table }, issues)
    }

    /**
     * 接続、指定テーブルの存在、エンジン固有の整合性を確認する。
     *
     * SQLite では `PRAGMA integrity_check` と `PRAGMA foreign_key_check` も実行します。チェックは
     * 読み取りのみで、データを変更しません。
     *
     * @param tables 存在を確認するテーブル。空の場合は接続とエンジン固有チェックのみ。
     */
    @JvmOverloads
    fun integrityCheck(tables: List<String> = emptyList()): ShimeDatabaseIntegrityReport {
        val checks = ArrayList<ShimeDatabaseIntegrityCheck>()
        val connected = try {
            database.healthCheck()
        } catch (_: Exception) {
            false
        }
        checks += ShimeDatabaseIntegrityCheck(
            name = "connection",
            passed = connected,
            details = if (connected) null else "Database health check failed."
        )
        if (!connected) {
            return ShimeDatabaseIntegrityReport(database.name, checks)
        }
        tables.forEach { table ->
            ShimeValidation.requireSafeKey(table, "integrity table")
            val exists = tableExists(table)
            checks += ShimeDatabaseIntegrityCheck(
                name = "table:$table",
                passed = exists,
                details = if (exists) null else "Table '$table' does not exist or is not readable."
            )
        }
        if (database.type == ShimeDatabaseType.SQLITE) {
            checks += sqliteCheck(
                name = "sqlite:integrity_check",
                sql = "PRAGMA integrity_check",
                expected = { values -> values.size == 1 && values.first().equals("ok", ignoreCase = true) },
                failureDetails = "SQLite reported integrity problems."
            )
            checks += sqliteCheck(
                name = "sqlite:foreign_key_check",
                sql = "PRAGMA foreign_key_check",
                expected = { values -> values.isEmpty() },
                failureDetails = "SQLite reported foreign key violations."
            )
        }
        return ShimeDatabaseIntegrityReport(database.name, checks)
    }

    /** 履歴テーブルを作成する。 */
    private fun ensureHistoryTable() {
        database.update(
            "CREATE TABLE IF NOT EXISTS $historyTable (" +
                "version INT NOT NULL PRIMARY KEY, " +
                "description VARCHAR(255) NOT NULL, " +
                "applied_at BIGINT NOT NULL)"
        )
    }

    private fun tableNames(): Set<String> {
        return try {
            database.transaction { connection ->
                val names = HashSet<String>()
                connection.metaData.getTables(null, null, "%", arrayOf("TABLE")).use { resultSet ->
                    while (resultSet.next()) {
                        names += resultSet.getString("TABLE_NAME")
                    }
                }
                names
            }
        } catch (exception: ShimeDatabaseException) {
            throw exception
        } catch (exception: Exception) {
            throw ShimeDatabaseException("Could not read table metadata from database '${database.name}'.", exception)
        }
    }

    private fun columnNames(table: String): Set<String> {
        return try {
            database.transaction { connection ->
                val names = HashSet<String>()
                connection.metaData.getColumns(null, null, table, "%").use { resultSet ->
                    while (resultSet.next()) {
                        names += resultSet.getString("COLUMN_NAME")
                    }
                }
                names
            }
        } catch (exception: ShimeDatabaseException) {
            throw exception
        } catch (exception: Exception) {
            throw ShimeDatabaseException("Could not read columns of '$table'.", exception)
        }
    }

    private fun sqliteCheck(
        name: String,
        sql: String,
        expected: (List<String>) -> Boolean,
        failureDetails: String
    ): ShimeDatabaseIntegrityCheck {
        return try {
            val values = database.query(sql) { resultSet -> resultSet.getString(1) }
            if (expected(values)) {
                ShimeDatabaseIntegrityCheck(name, true)
            } else {
                ShimeDatabaseIntegrityCheck(name, false, failureDetails)
            }
        } catch (exception: ShimeDatabaseException) {
            ShimeDatabaseIntegrityCheck(name, false, exception.message ?: failureDetails)
        }
    }
}
