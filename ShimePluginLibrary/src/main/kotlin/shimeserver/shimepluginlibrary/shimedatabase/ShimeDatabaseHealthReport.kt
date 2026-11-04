package shimeserver.shimepluginlibrary.shimedatabase

/**
 * データベース 1 件分のヘルスレポート。
 *
 * [healthy] はレポート生成時点の接続確認結果です。[stats] は累計の診断カウンターであり、
 * パスワードや接続文字列などの機密情報は含みません。
 */
data class ShimeDatabaseHealthReport(
    val name: String,
    val type: ShimeDatabaseType,
    val ready: Boolean,
    val healthy: Boolean,
    val stats: ShimeDatabaseStats
) {
    init {
        require(name.isNotBlank()) { "Database name must not be blank." }
    }

    /** 診断向けの短い要約。 */
    fun summary(): String {
        return "Database '$name' ($type) ready=$ready healthy=$healthy " +
            "operations=${stats.totalOperations} failures=${stats.failures}"
    }
}
