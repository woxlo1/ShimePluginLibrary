package shimeserver.shimepluginlibrary.shimecache

/** キャッシュ実装が公開する統計情報。 */
data class ShimeCacheStats(
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val expirations: Long,
    val size: Int,
    val capacity: Int
) {
    val hitRate: Double
        get() {
            val total = hits + misses
            return if (total == 0L) 0.0 else hits.toDouble() / total
        }
}
