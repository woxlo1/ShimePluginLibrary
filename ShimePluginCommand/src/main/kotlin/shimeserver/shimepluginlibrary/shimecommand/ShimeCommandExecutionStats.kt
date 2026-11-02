package shimeserver.shimepluginlibrary.shimecommand

/**
 * コマンドパスごとの実行統計。
 *
 * 拒否された呼び出し（権限不足、クールダウン、引数エラーなど）は含まず、実際にエグゼキューターが
 * 実行された呼び出しだけを集計します。
 */
data class ShimeCommandExecutionStats(
    val path: String,
    val executions: Long,
    val successes: Long,
    val failures: Long,
    val totalDurationNanos: Long,
    val lastExecutedMillis: Long
) {
    init {
        require(executions >= 0) { "Execution count must not be negative." }
        require(successes >= 0) { "Success count must not be negative." }
        require(failures >= 0) { "Failure count must not be negative." }
        require(totalDurationNanos >= 0) { "Total duration must not be negative." }
    }

    /** 実行 1 回あたりの平均時間。実行がない場合は 0。 */
    val averageDurationMillis: Double
        get() = if (executions == 0L) 0.0 else totalDurationNanos.toDouble() / executions / 1_000_000.0
}
