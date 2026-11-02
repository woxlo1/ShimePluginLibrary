package shimeserver.shimepluginlibrary.shimedatabase

import java.sql.Connection
import java.sql.ResultSet
import java.util.concurrent.CompletableFuture

/**
 * JDBC データベースのファサード。
 *
 * パラメーター付きの各メソッドは値を [java.sql.PreparedStatement] 経由でバインドし、SQL テキストを
 * 呼び出し側が渡した値から組み立てることはない。非同期メソッドはデータベースの上限付きワーカープールで
 * 実行される。
 */
interface ShimeDatabase : AutoCloseable {
    val name: String

    val type: ShimeDatabaseType

    val isReady: Boolean

    fun initialize()

    fun <T> query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): List<T>

    fun update(sql: String, vararg params: Any?): Int

    fun batch(sql: String, batches: List<List<Any?>>): IntArray

    fun batchAsync(sql: String, batches: List<List<Any?>>): CompletableFuture<IntArray>

    fun <T> transaction(block: (Connection) -> T): T

    fun <T> transactionAsync(block: (Connection) -> T): CompletableFuture<T>

    fun <T> queryAsync(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): CompletableFuture<List<T>>

    fun updateAsync(sql: String, vararg params: Any?): CompletableFuture<Int>

    fun healthCheck(): Boolean

    /** クエリ実行時間や失敗件数を含む診断スナップショットを返す。 */
    fun stats(): ShimeDatabaseStats

    override fun close()
}
