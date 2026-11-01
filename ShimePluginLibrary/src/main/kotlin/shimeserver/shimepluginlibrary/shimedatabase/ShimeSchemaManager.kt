package shimeserver.shimepluginlibrary.shimedatabase

import shimeserver.shimepluginlibrary.shimeexception.ShimeDatabaseException
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation

/**
 * 順序付けられたスキーマ移行を適用し、そのバージョンを記録する。
 *
 * このマネージャーはプラグイン開発者が提供した SQL のみを実行し、プレイヤーや設定ファイルからの SQL を
 * 受け付けることはない。該当する場合、値は引き続き呼び出し側でバインドされることが想定されている。
 */
class ShimeSchemaManager(
    private val database: ShimeDatabase,
    private val historyTable: String = "shime_schema_history"
) {
    init {
        ShimeValidation.requireSafeKey(historyTable, "history table")
    }

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

    fun appliedVersions(): Set<Int> {
        ensureHistoryTable()
        return database.query("SELECT version FROM $historyTable") { resultSet ->
            resultSet.getInt("version")
        }.toSet()
    }

    fun tableExists(table: String): Boolean {
        ShimeValidation.requireSafeKey(table, "table")
        return try {
            database.query("SELECT 1 FROM $table WHERE 1 = 0") { 1 }
            true
        } catch (_: ShimeDatabaseException) {
            false
        }
    }

    private fun ensureHistoryTable() {
        database.update(
            "CREATE TABLE IF NOT EXISTS $historyTable (" +
                "version INT NOT NULL PRIMARY KEY, " +
                "description VARCHAR(255) NOT NULL, " +
                "applied_at BIGINT NOT NULL)"
        )
    }
}
