package shimeserver.shimepluginlibrary.shimedatabase

/**
 * データベース操作の診断カウンターのスナップショット。
 *
 * 値は単調増加する累計です。平均時間はクエリと更新の実行時間をまとめたものです。機密情報は含まれません。
 */
data class ShimeDatabaseStats(
    val queries: Long = 0,
    val updates: Long = 0,
    val batches: Long = 0,
    val transactions: Long = 0,
    val failures: Long = 0,
    val slowQueries: Long = 0,
    val totalExecutionNanos: Long = 0
) {
    val totalOperations: Long
        get() = queries + updates + batches + transactions

    val averageExecutionMillis: Double
        get() {
            val operations = queries + updates + transactions
            return if (operations == 0L) 0.0 else totalExecutionNanos.toDouble() / operations / 1_000_000.0
        }

    override fun toString(): String {
        return "ShimeDatabaseStats(queries=$queries, updates=$updates, batches=$batches, " +
            "transactions=$transactions, failures=$failures, slowQueries=$slowQueries, " +
            "averageExecutionMillis=${"%.3f".format(averageExecutionMillis)})"
    }
}
