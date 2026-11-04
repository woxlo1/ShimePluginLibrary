package shimeserver.shimepluginlibrary.shimetask

import shimeserver.shimepluginlibrary.shimescheduler.ShimeTaskHandle
import java.util.concurrent.CompletableFuture

/**
 * [ShimeTaskManager] によって管理される名前付きタスク。
 *
 * 状態遷移はマネージャーだけが行い、外部からは読み取り専用として参照します。
 * [future] を取得すると、完了結果を非同期に待ち受けられます。
 */
class ShimeManagedTask(
    val name: String,
    val group: String,
    handle: ShimeTaskHandle?,
    val priority: ShimeTaskPriority = ShimeTaskPriority.NORMAL,
    val dependencies: List<String> = emptyList(),
    val timeoutTicks: Long = 0L,
    val future: CompletableFuture<ShimeTaskResult<*>>? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    @Volatile
    var handle: ShimeTaskHandle? = handle
        internal set

    @Volatile
    var state: ShimeTaskState = ShimeTaskState.SCHEDULED
        internal set

    @Volatile
    var startedAtMillis: Long = 0L
        internal set

    @Volatile
    var finishedAtMillis: Long = 0L
        internal set

    @Volatile
    var durationNanos: Long = 0L
        internal set

    val isCancelled: Boolean
        get() = state == ShimeTaskState.CANCELLED ||
            state == ShimeTaskState.TIMED_OUT ||
            state == ShimeTaskState.SKIPPED ||
            (state != ShimeTaskState.COMPLETED && state != ShimeTaskState.FAILED && handle?.isCancelled == true)

    val isDone: Boolean
        get() = state == ShimeTaskState.COMPLETED ||
            state == ShimeTaskState.FAILED ||
            state == ShimeTaskState.CANCELLED ||
            state == ShimeTaskState.TIMED_OUT ||
            state == ShimeTaskState.SKIPPED

    /** 実行中であれば経過ナノ秒を返す。 */
    fun elapsedNanos(): Long {
        val started = startedAtMillis
        if (started <= 0L) {
            return 0L
        }
        if (finishedAtMillis > 0L) {
            return durationNanos
        }
        return ((System.currentTimeMillis() - started) * 1_000_000L).coerceAtLeast(0L)
    }

    /**
     * タスクをキャンセルする。すでに完了している場合は何もしない。
     *
     * @return 実際にキャンセルした場合 true。
     */
    fun cancel(): Boolean {
        if (isDone) {
            return false
        }
        state = ShimeTaskState.CANCELLED
        finishedAtMillis = System.currentTimeMillis()
        handle?.cancel()
        completeCancelled()
        return true
    }

    /** 監視用スナップショットを作る。 */
    fun snapshot(): ShimeTaskSnapshot {
        return ShimeTaskSnapshot(
            name = name,
            group = group,
            priority = priority,
            state = state,
            dependencies = dependencies.toList(),
            createdAtMillis = createdAtMillis,
            startedAtMillis = startedAtMillis,
            elapsedNanos = elapsedNanos()
        )
    }

    internal fun completeCancelled() {
        val pending = future ?: return
        pending.complete(
            ShimeTaskResult<Any?>(
                name = name,
                state = ShimeTaskState.CANCELLED,
                startedAtMillis = if (startedAtMillis > 0L) startedAtMillis else createdAtMillis,
                finishedAtMillis = finishedAtMillis.coerceAtLeast(createdAtMillis),
                durationNanos = durationNanos
            )
        )
    }
}
