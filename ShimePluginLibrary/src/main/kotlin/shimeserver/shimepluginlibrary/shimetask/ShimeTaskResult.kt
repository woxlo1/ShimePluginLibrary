package shimeserver.shimepluginlibrary.shimetask

import shimeserver.shimepluginlibrary.shimeexception.ShimeException

/** タスクの実行状態。 */
enum class ShimeTaskState {
    /** 依存タスクの完了や遅延時間の経過を待っている。 */
    SCHEDULED,
    /** 現在実行中。 */
    RUNNING,
    /** 正常に完了した。 */
    COMPLETED,
    /** 例外で失敗した。 */
    FAILED,
    /** 明示的にキャンセルされた。 */
    CANCELLED,
    /** タイムアウトにより中断された。 */
    TIMED_OUT,
    /** 依存タスクが成功しなかったため実行されなかった。 */
    SKIPPED
}

/** タスクの優先度。数値が大きいほど先に実行される。 */
enum class ShimeTaskPriority(val weight: Int) {
    LOWEST(0),
    LOW(25),
    NORMAL(50),
    HIGH(75),
    HIGHEST(100)
}

/**
 * タスク 1 回分の実行結果。
 *
 * [value] は正常完了時のみ設定され、[error] は失敗・タイムアウト時に設定されます。
 */
data class ShimeTaskResult<T>(
    val name: String,
    val state: ShimeTaskState,
    val value: T? = null,
    val error: Throwable? = null,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val durationNanos: Long
) {
    init {
        require(name.isNotBlank()) { "Task result name must not be blank." }
        require(finishedAtMillis >= startedAtMillis) { "Task finish time must not precede its start time." }
        require(durationNanos >= 0L) { "Task duration must not be negative." }
        require(state == ShimeTaskState.COMPLETED || value == null) {
            "Only completed tasks may expose a value."
        }
        require(
            state == ShimeTaskState.FAILED ||
                state == ShimeTaskState.TIMED_OUT ||
                state == ShimeTaskState.SKIPPED ||
                error == null
        ) {
            "Only failed, timed out or skipped tasks may expose an error."
        }
    }

    val isSuccess: Boolean
        get() = state == ShimeTaskState.COMPLETED

    /** 実行結果を返す。失敗していた場合は原因を包んだ例外をスローする。 */
    fun getOrThrow(): T {
        if (state != ShimeTaskState.COMPLETED) {
            throw ShimeTaskException("Task '$name' did not complete successfully ($state).", error)
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}

/** タスクの登録・実行に関する例外。 */
open class ShimeTaskException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** タスクが制限時間内に完了しなかったことを表す。 */
class ShimeTaskTimeoutException(message: String, cause: Throwable? = null) : ShimeTaskException(message, cause)

/** 依存タスクが成功しなかったことを表す。 */
class ShimeTaskDependencyException(message: String, cause: Throwable? = null) : ShimeTaskException(message, cause)

/** 診断用のタスクカウンターのスナップショット。 */
data class ShimeTaskStatistics(
    val submitted: Long,
    val cancelled: Long,
    val completed: Long = 0L,
    val failed: Long = 0L,
    val timedOut: Long = 0L,
    val skipped: Long = 0L,
    val active: Int,
    val running: Int = 0,
    val totalDurationNanos: Long = 0L
) {
    init {
        require(submitted >= 0L) { "Submitted count must not be negative." }
        require(cancelled >= 0L) { "Cancelled count must not be negative." }
        require(active >= 0) { "Active task count must not be negative." }
        require(running >= 0) { "Running task count must not be negative." }
        require(totalDurationNanos >= 0L) { "Total duration must not be negative." }
    }

    /** 完了したタスク 1 件あたりの平均実行時間。 */
    val averageDurationNanos: Long
        get() {
            val finished = completed + failed + timedOut
            return if (finished == 0L) 0L else totalDurationNanos / finished
        }
}

/** 実行中タスクの監視用スナップショット。 */
data class ShimeTaskSnapshot(
    val name: String,
    val group: String,
    val priority: ShimeTaskPriority,
    val state: ShimeTaskState,
    val dependencies: List<String>,
    val createdAtMillis: Long,
    val startedAtMillis: Long,
    val elapsedNanos: Long
)
