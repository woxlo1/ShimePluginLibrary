package shimeserver.shimepluginlibrary.shimedatabase

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeexception.ShimeDatabaseException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.concurrent.ConcurrentHashMap

/** 名前付きデータベースのレジストリおよびライフサイクルの所有者。 */
class ShimeDatabaseManager(
    private val logger: ShimeLogger
) : ShimeAbstractService(SERVICE_NAME) {
    private val databases = ConcurrentHashMap<String, ShimeDatabase>()

    override fun onInitialize(context: ShimeContext) {
        // データベースはプラグインまたは機能モジュールが connect() を通じて接続する。
    }

    override fun onShutdown() {
        closeAll()
    }

    fun connect(name: String, settings: ShimeDatabaseSettings): ShimeDatabase {
        require(name.isNotBlank()) { "Database name must not be blank." }
        val database = ShimeSqlDatabase(name, settings, logger)
        database.initialize()
        register(name, database, replace = false)
        return database
    }

    fun register(name: String, database: ShimeDatabase, replace: Boolean = false) {
        val previous = databases.put(name, database)
        if (previous != null && !replace) {
            databases[name] = previous
            throw ShimeDatabaseException("Database '$name' is already registered.")
        }
        if (previous != null && previous !== database) {
            previous.close()
        }
    }

    fun get(name: String): ShimeDatabase? = databases[name]

    fun require(name: String): ShimeDatabase {
        return get(name) ?: throw ShimeDatabaseException(
            "Database '$name' is not registered. Available databases: ${names().joinToString().ifBlank { "none" }}."
        )
    }

    fun default(): ShimeDatabase? = databases.values.firstOrNull { it.isReady }

    fun disconnect(name: String): Boolean {
        val database = databases.remove(name) ?: return false
        database.close()
        return true
    }

    fun closeAll() {
        databases.values.forEach { database ->
            try {
                database.close()
            } catch (exception: Exception) {
                logger.error(ShimeLogCategory.DATABASE, "Could not close database '${database.name}'.", exception)
            }
        }
        databases.clear()
    }

    fun health(): Map<String, Boolean> = databases.mapValues { (_, database) -> database.healthCheck() }

    /**
     * 登録済みデータベースごとのヘルスレポートを名前順に返す。
     *
     * 接続確認に失敗しても例外は投げず、[ShimeDatabaseHealthReport.healthy] が false になります。
     * レポートには累計統計が含まれますが、パスワードや接続文字列は含まれません。
     */
    fun healthReports(): List<ShimeDatabaseHealthReport> {
        return names().map { databaseName ->
            val database = databases.getValue(databaseName)
            val healthy = try {
                database.healthCheck()
            } catch (exception: Exception) {
                logger.warn(
                    ShimeLogCategory.DATABASE,
                    "Health check failed for database '$databaseName'.",
                    exception
                )
                false
            }
            ShimeDatabaseHealthReport(
                name = databaseName,
                type = database.type,
                ready = database.isReady,
                healthy = healthy,
                stats = database.stats()
            )
        }
    }

    /** [name] のヘルスレポート。未登録の場合は null。 */
    fun healthReport(name: String): ShimeDatabaseHealthReport? = healthReports().firstOrNull { it.name == name }

    /** 登録済みデータベースがすべて正常な場合に true。データベースが未登録の場合は true。 */
    fun isHealthy(): Boolean = healthReports().all { it.healthy }

    fun names(): List<String> = databases.keys.sorted()

    companion object {
        const val SERVICE_NAME = "database"
    }
}
