package shimeserver.shimepluginlibrary.shimedatabase

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import shimeserver.shimepluginlibrary.shimeexception.ShimeDatabaseException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * SQLite、MySQL、MariaDB 向けの HikariCP ベースの実装。
 *
 * SQLite は組み込みエンジンが同時書き込みをサポートしないため、単一接続で設定される。リモートデータベースは
 * 設定されたプールサイズを使用する。各操作の実行時間と失敗件数は [stats] から取得でき、閾値を超えた
 * 操作は SQL 本文やバインド値を含めずに警告として記録される。
 */
class ShimeSqlDatabase(
    override val name: String,
    private val settings: ShimeDatabaseSettings,
    private val logger: ShimeLogger
) : ShimeDatabase {
    private val ready = AtomicBoolean(false)
    private var dataSource: HikariDataSource? = null
    private var executor: ExecutorService? = null

    private val queryCount = AtomicLong()
    private val updateCount = AtomicLong()
    private val batchCount = AtomicLong()
    private val transactionCount = AtomicLong()
    private val failureCount = AtomicLong()
    private val slowQueryCount = AtomicLong()
    private val totalExecutionNanos = AtomicLong()

    override val type: ShimeDatabaseType
        get() = settings.type

    override val isReady: Boolean
        get() = ready.get()

    override fun initialize() {
        if (ready.get()) {
            return
        }
        try {
            val hikari = HikariConfig().apply {
                jdbcUrl = settings.jdbcUrl
                driverClassName = settings.driverClassName
                poolName = "shime-$name"
                maximumPoolSize = if (settings.type == ShimeDatabaseType.SQLITE) 1 else settings.poolSize
                minimumIdle = 1
                connectionTimeout = settings.connectionTimeoutMillis
                validationTimeout = settings.connectionTimeoutMillis.coerceAtMost(5_000)
                maxLifetime = settings.maxLifetimeMillis
                isAutoCommit = true
                if (settings.type != ShimeDatabaseType.SQLITE) {
                    username = settings.username
                    password = settings.password
                }
                settings.extraProperties.forEach { (key, value) -> addDataSourceProperty(key, value) }
                if (settings.type == ShimeDatabaseType.SQLITE) {
                    addDataSourceProperty("journal_mode", "WAL")
                    addDataSourceProperty("busy_timeout", "5000")
                    addDataSourceProperty("foreign_keys", "true")
                }
            }
            dataSource = HikariDataSource(hikari)
            executor = Executors.newFixedThreadPool(
                (if (settings.type == ShimeDatabaseType.SQLITE) 1 else settings.poolSize)
                    .coerceIn(1, 8),
                Thread.ofPlatform().name("shime-db-$name-", 0).daemon(true).factory()
            )
            ready.set(true)
            logger.info(ShimeLogCategory.DATABASE, "Database '$name' (${settings.type.displayName}) is ready.")
        } catch (exception: Exception) {
            close()
            throw ShimeDatabaseException("Could not initialize database '$name'.", exception)
        }
    }

    override fun <T> query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): List<T> {
        requireReady("query")
        requireSql(sql)
        return measured(queryCount, "query") {
            try {
                dataSource!!.connection.use { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        ShimeSqlBinder.bind(statement, params.toList())
                        statement.queryTimeout = settings.queryTimeoutSeconds
                        statement.executeQuery().use { resultSet ->
                            val results = ArrayList<T>()
                            while (resultSet.next()) {
                                results.add(mapper(resultSet))
                            }
                            results
                        }
                    }
                }
            } catch (exception: SQLException) {
                throw ShimeDatabaseException("Query failed on database '$name'.", exception)
            }
        }
    }

    override fun update(sql: String, vararg params: Any?): Int {
        requireReady("update")
        requireSql(sql)
        return measured(updateCount, "update") {
            try {
                dataSource!!.connection.use { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        ShimeSqlBinder.bind(statement, params.toList())
                        statement.queryTimeout = settings.queryTimeoutSeconds
                        statement.executeUpdate()
                    }
                }
            } catch (exception: SQLException) {
                throw ShimeDatabaseException("Update failed on database '$name'.", exception)
            }
        }
    }

    override fun batch(sql: String, batches: List<List<Any?>>): IntArray {
        requireReady("batch update")
        requireSql(sql)
        if (batches.isEmpty()) {
            return IntArray(0)
        }
        return measured(batchCount, "batch update") {
            try {
                dataSource!!.connection.use { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.queryTimeout = settings.queryTimeoutSeconds
                        for (parameters in batches) {
                            ShimeSqlBinder.bind(statement, parameters)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
            } catch (exception: SQLException) {
                throw ShimeDatabaseException("Batch update failed on database '$name'.", exception)
            }
        }
    }

    override fun batchAsync(sql: String, batches: List<List<Any?>>): CompletableFuture<IntArray> {
        val pool = executor ?: throw ShimeDatabaseException("Database '$name' is not initialized.")
        return CompletableFuture.supplyAsync({ batch(sql, batches) }, pool)
    }

    override fun <T> transaction(block: (Connection) -> T): T {
        requireReady("transaction")
        return measured(transactionCount, "transaction") {
            try {
                dataSource!!.connection.use { connection ->
                    val previousAutoCommit = connection.autoCommit
                    connection.autoCommit = false
                    try {
                        val result = block(connection)
                        connection.commit()
                        result
                    } catch (throwable: Throwable) {
                        try {
                            connection.rollback()
                        } catch (rollbackFailure: SQLException) {
                            throwable.addSuppressed(rollbackFailure)
                        }
                        throw throwable
                    } finally {
                        connection.autoCommit = previousAutoCommit
                    }
                }
            } catch (exception: ShimeDatabaseException) {
                throw exception
            } catch (exception: shimeserver.shimepluginlibrary.shimeexception.ShimeException) {
                throw exception
            } catch (exception: Throwable) {
                throw ShimeDatabaseException("Transaction failed on database '$name'.", exception)
            }
        }
    }

    override fun <T> transactionAsync(block: (Connection) -> T): CompletableFuture<T> {
        val pool = executor ?: throw ShimeDatabaseException("Database '$name' is not initialized.")
        return CompletableFuture.supplyAsync({ transaction(block) }, pool)
    }

    override fun <T> queryAsync(
        sql: String,
        vararg params: Any?,
        mapper: (ResultSet) -> T
    ): CompletableFuture<List<T>> {
        val pool = executor ?: throw ShimeDatabaseException("Database '$name' is not initialized.")
        return CompletableFuture.supplyAsync({ query(sql, *params, mapper = mapper) }, pool)
    }

    override fun updateAsync(sql: String, vararg params: Any?): CompletableFuture<Int> {
        val pool = executor ?: throw ShimeDatabaseException("Database '$name' is not initialized.")
        return CompletableFuture.supplyAsync({ update(sql, *params) }, pool)
    }

    override fun healthCheck(): Boolean {
        if (!ready.get()) {
            return false
        }
        return try {
            dataSource?.connection?.use { connection ->
                connection.createStatement().use { statement ->
                    statement.queryTimeout = settings.queryTimeoutSeconds
                    statement.executeQuery("SELECT 1").use { resultSet -> resultSet.next() }
                }
            } ?: false
        } catch (exception: SQLException) {
            failureCount.incrementAndGet()
            logger.warn(ShimeLogCategory.DATABASE, "Health check failed for database '$name'.", exception)
            false
        }
    }

    override fun stats(): ShimeDatabaseStats {
        return ShimeDatabaseStats(
            queries = queryCount.get(),
            updates = updateCount.get(),
            batches = batchCount.get(),
            transactions = transactionCount.get(),
            failures = failureCount.get(),
            slowQueries = slowQueryCount.get(),
            totalExecutionNanos = totalExecutionNanos.get()
        )
    }

    override fun close() {
        ready.set(false)
        try {
            dataSource?.close()
        } catch (exception: Exception) {
            logger.warn(ShimeLogCategory.DATABASE, "Could not close database '$name'.", exception)
        } finally {
            dataSource = null
        }
        executor?.let { pool ->
            pool.shutdown()
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow()
                }
            } catch (exception: InterruptedException) {
                pool.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        executor = null
    }

    private inline fun <T> measured(counter: AtomicLong, operation: String, block: () -> T): T {
        val started = System.nanoTime()
        try {
            return block()
        } catch (throwable: Throwable) {
            failureCount.incrementAndGet()
            throw throwable
        } finally {
            val elapsed = System.nanoTime() - started
            counter.incrementAndGet()
            totalExecutionNanos.addAndGet(elapsed)
            val threshold = settings.slowQueryThresholdMillis
            if (threshold > 0 && elapsed >= TimeUnit.MILLISECONDS.toNanos(threshold)) {
                slowQueryCount.incrementAndGet()
                logger.warn(
                    ShimeLogCategory.DATABASE,
                    "Slow $operation on database '$name' took ${elapsed / 1_000_000} ms."
                )
            }
        }
    }

    private fun requireReady(operation: String) {
        if (!ready.get()) {
            throw ShimeDatabaseException("Database '$name' cannot perform '$operation' before initialization.")
        }
    }

    private fun requireSql(sql: String) {
        if (sql.isBlank()) {
            throw ShimeDatabaseException("SQL must not be blank.")
        }
    }
}
