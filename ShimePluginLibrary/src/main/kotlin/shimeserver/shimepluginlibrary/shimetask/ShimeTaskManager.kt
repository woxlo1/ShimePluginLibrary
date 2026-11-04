package shimeserver.shimepluginlibrary.shimetask

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimescheduler.ShimeTaskHandle
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * [ShimeScheduler] 上に構築された名前付きタスクレジストリ。
 *
 * 名前はマネージャーごとに一意である。グループにより、無関係なタスクに触れることなく
 * ある機能の処理をキャンセルできる。すべてのタスクは停止時にキャンセルされる。
 *
 * [submitAsyncFuture] などの Future 系 API は実行結果・失敗・タイムアウトを
 * [ShimeTaskResult] として返し、依存関係や監視に対応する。Bukkit API を触る処理は
 * [submitSyncFuture] または [callSync] を使ってプライマリスレッド上で実行すること。
 */
class ShimeTaskManager(
    private val scheduler: ShimeScheduler
) : ShimeAbstractService(SERVICE_NAME) {
    private val tasks = ConcurrentHashMap<String, ShimeManagedTask>()
    private val sequence = AtomicLong()
    private val submitted = AtomicLong()
    private val cancelled = AtomicLong()
    private val completed = AtomicLong()
    private val failed = AtomicLong()
    private val timedOut = AtomicLong()
    private val skipped = AtomicLong()
    private val totalDurationNanos = AtomicLong()

    override fun onInitialize(context: ShimeContext) {
        // スケジューラーはサービス初期化前にライブラリによって作成される。
    }

    override fun onShutdown() {
        cancelAll()
    }

    fun submitSync(name: String, group: String = "default", task: () -> Unit): ShimeManagedTask {
        submitSyncFuture(name, group) { task() }
        return requireManaged(name)
    }

    fun submitAsync(name: String, group: String = "default", task: () -> Unit): ShimeManagedTask {
        submitAsyncFuture(name, group) { task() }
        return requireManaged(name)
    }

    fun schedule(name: String, delayTicks: Long, group: String = "default", task: () -> Unit): ShimeManagedTask {
        scheduleFuture(name, delayTicks, group) { task() }
        return requireManaged(name)
    }

    fun scheduleAsync(
        name: String,
        delayTicks: Long,
        group: String = "default",
        task: () -> Unit
    ): ShimeManagedTask {
        scheduleAsyncFuture(name, delayTicks, group) { task() }
        return requireManaged(name)
    }

    fun repeat(
        name: String,
        delayTicks: Long,
        periodTicks: Long,
        group: String = "default",
        task: () -> Unit
    ): ShimeManagedTask {
        requireName(name)
        val handle = scheduler.runRepeating(delayTicks, periodTicks, task)
        return rememberRepeating(name, group, handle)
    }

    fun repeatAsync(
        name: String,
        delayTicks: Long,
        periodTicks: Long,
        group: String = "default",
        task: () -> Unit
    ): ShimeManagedTask {
        requireName(name)
        val handle = scheduler.runAsyncRepeating(delayTicks, periodTicks, task)
        return rememberRepeating(name, group, handle)
    }

    /**
     * プライマリスレッドで実行し、結果を Future で返す。
     *
     * @param dependsOn このタスクより先に完了していなければならないタスク名。登録済みであること。
     * @param timeoutTicks タイムアウトまでの tick 数。0 以下はタイムアウトなし。
     */
    @JvmOverloads
    fun <T> submitSyncFuture(
        name: String,
        group: String = "default",
        priority: ShimeTaskPriority = ShimeTaskPriority.NORMAL,
        dependsOn: List<String> = emptyList(),
        timeoutTicks: Long = 0L,
        task: () -> T
    ): CompletableFuture<ShimeTaskResult<T>> {
        return submitFuture(name, group, priority, dependsOn, timeoutTicks, 0L, async = false, task)
    }

    /**
     * 非同期スレッドで実行し、結果を Future で返す。
     *
     * 非同期タスクから Bukkit API を直接操作してはいけない。必要な場合は [callSync] を使う。
     */
    @JvmOverloads
    fun <T> submitAsyncFuture(
        name: String,
        group: String = "default",
        priority: ShimeTaskPriority = ShimeTaskPriority.NORMAL,
        dependsOn: List<String> = emptyList(),
        timeoutTicks: Long = 0L,
        task: () -> T
    ): CompletableFuture<ShimeTaskResult<T>> {
        return submitFuture(name, group, priority, dependsOn, timeoutTicks, 0L, async = true, task)
    }

    /** 指定 tick 後にプライマリスレッドで実行し、結果を Future で返す。 */
    @JvmOverloads
    fun <T> scheduleFuture(
        name: String,
        delayTicks: Long,
        group: String = "default",
        priority: ShimeTaskPriority = ShimeTaskPriority.NORMAL,
        dependsOn: List<String> = emptyList(),
        timeoutTicks: Long = 0L,
        task: () -> T
    ): CompletableFuture<ShimeTaskResult<T>> {
        require(delayTicks >= 0L) { "Delay must not be negative." }
        return submitFuture(name, group, priority, dependsOn, timeoutTicks, delayTicks, async = false, task)
    }

    /** 指定 tick 後に非同期スレッドで実行し、結果を Future で返す。 */
    @JvmOverloads
    fun <T> scheduleAsyncFuture(
        name: String,
        delayTicks: Long,
        group: String = "default",
        priority: ShimeTaskPriority = ShimeTaskPriority.NORMAL,
        dependsOn: List<String> = emptyList(),
        timeoutTicks: Long = 0L,
        task: () -> T
    ): CompletableFuture<ShimeTaskResult<T>> {
        require(delayTicks >= 0L) { "Delay must not be negative." }
        return submitFuture(name, group, priority, dependsOn, timeoutTicks, delayTicks, async = true, task)
    }

    /** プライマリスレッドで [supplier] を実行し、戻り値を Future で返す。 */
    fun <T> callSync(supplier: () -> T): CompletableFuture<T> = scheduler.callSync(supplier)

    /** [name] のタスクをキャンセルする。完了済みの場合は false。 */
    fun cancel(name: String): Boolean {
        val task = tasks[name] ?: return false
        val didCancel = task.cancel()
        if (didCancel) {
            cancelled.incrementAndGet()
        }
        return didCancel
    }

    fun cancelGroup(group: String): Int {
        val matching = tasks.values.filter { it.group == group && !it.isDone }
        var count = 0
        matching.forEach { if (it.cancel()) count += 1 }
        cancelled.addAndGet(count.toLong())
        return count
    }

    fun cancelAll() {
        val snapshot = tasks.values.toList()
        var count = 0
        snapshot.forEach { if (it.cancel()) count += 1 }
        tasks.clear()
        cancelled.addAndGet(count.toLong())
    }

    /** 実行中または待機中のタスクを返す。完了済みは含まれない。 */
    fun get(name: String): ShimeManagedTask? = tasks[name]?.takeUnless { it.isDone }

    /** 実行中または待機中のタスク一覧。 */
    fun activeTasks(): List<ShimeManagedTask> = tasks.values.filterNot { it.isDone }

    /** 実行中のタスク一覧。 */
    fun runningTasks(): List<ShimeManagedTask> = activeTasks().filter { it.state == ShimeTaskState.RUNNING }

    /** [name] の監視用スナップショット。未登録の場合は null。 */
    fun snapshot(name: String): ShimeTaskSnapshot? = tasks[name]?.snapshot()

    /** 監視用スナップショットを名前順に返す。 */
    fun snapshots(): List<ShimeTaskSnapshot> = tasks.values.map { it.snapshot() }.sortedBy { it.name }

    fun statistics(): ShimeTaskStatistics {
        val active = tasks.values.count { !it.isDone }
        val running = tasks.values.count { it.state == ShimeTaskState.RUNNING }
        return ShimeTaskStatistics(
            submitted = submitted.get(),
            cancelled = cancelled.get(),
            completed = completed.get(),
            failed = failed.get(),
            timedOut = timedOut.get(),
            skipped = skipped.get(),
            active = active,
            running = running,
            totalDurationNanos = totalDurationNanos.get()
        )
    }

    private fun <T> submitFuture(
        name: String,
        group: String,
        priority: ShimeTaskPriority,
        dependsOn: List<String>,
        timeoutTicks: Long,
        delayTicks: Long,
        async: Boolean,
        task: () -> T
    ): CompletableFuture<ShimeTaskResult<T>> {
        requireName(name)
        require(timeoutTicks >= 0L) { "Timeout must not be negative." }
        val dependencies = dependsOn.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(dependencies.none { it == name }) { "Task '$name' cannot depend on itself." }
        dependencies.forEach { dependency ->
            require(tasks.containsKey(dependency)) {
                "Task '$name' depends on unregistered task '$dependency'. Register dependencies first."
            }
        }

        val future = CompletableFuture<ShimeTaskResult<T>>()
        @Suppress("UNCHECKED_CAST")
        val managed = ShimeManagedTask(
            name = name,
            group = group,
            handle = null,
            priority = priority,
            dependencies = dependencies,
            timeoutTicks = timeoutTicks,
            future = future as CompletableFuture<ShimeTaskResult<*>>
        )
        remember(managed)
        future.whenComplete { result, _ ->
            if (result != null) {
                when (result.state) {
                    ShimeTaskState.COMPLETED -> completed.incrementAndGet()
                    ShimeTaskState.FAILED -> failed.incrementAndGet()
                    ShimeTaskState.TIMED_OUT -> timedOut.incrementAndGet()
                    ShimeTaskState.SKIPPED -> skipped.incrementAndGet()
                    else -> Unit
                }
                totalDurationNanos.addAndGet(result.durationNanos)
            }
            pruneCompleted()
        }

        val dependencyFutures = dependencies.map { dependency -> tasks.getValue(dependency).future }
        if (dependencyFutures.any { it == null }) {
            markSkipped(
                managed,
                ShimeTaskDependencyException(
                    "Task '$name' has a dependency that was registered without a future."
                )
            )
            return future
        }

        if (dependencyFutures.isEmpty()) {
            startFuture(managed, timeoutTicks, delayTicks, async, task)
            return future
        }

        CompletableFuture.allOf(*dependencyFutures.map { it!! }.toTypedArray()).whenComplete { _, _ ->
            val unsuccessful = dependencies.mapNotNull { tasks[it] }
                .firstOrNull { dependency ->
                    dependency.future == null ||
                        dependency.future.isCompletedExceptionally ||
                        dependency.future.getNow(null)?.state != ShimeTaskState.COMPLETED
                }
            if (unsuccessful != null) {
                markSkipped(
                    managed,
                    ShimeTaskDependencyException(
                        "Task '$name' depends on '${unsuccessful.name}', which did not complete successfully."
                    )
                )
            } else {
                startFuture(managed, timeoutTicks, delayTicks, async, task)
            }
        }
        return future
    }

    private fun <T> startFuture(
        managed: ShimeManagedTask,
        timeoutTicks: Long,
        delayTicks: Long,
        async: Boolean,
        task: () -> T
    ) {
        if (managed.state == ShimeTaskState.CANCELLED) {
            return
        }
        managed.state = ShimeTaskState.RUNNING
        managed.startedAtMillis = System.currentTimeMillis()
        val started = System.nanoTime()
        var timeoutHandle: ShimeTaskHandle? = null
        if (timeoutTicks > 0L) {
            timeoutHandle = scheduler.runLater(timeoutTicks) {
                val future = managed.future ?: return@runLater
                if (future.isDone) {
                    return@runLater
                }
                val now = System.currentTimeMillis()
                managed.state = ShimeTaskState.TIMED_OUT
                managed.finishedAtMillis = now
                managed.durationNanos = (System.nanoTime() - started).coerceAtLeast(0L)
                managed.handle?.cancel()
                future.complete(
                    ShimeTaskResult<Any?>(
                        name = managed.name,
                        state = ShimeTaskState.TIMED_OUT,
                        error = ShimeTaskTimeoutException(
                            "Task '${managed.name}' exceeded its timeout of $timeoutTicks tick(s)."
                        ),
                        startedAtMillis = managed.startedAtMillis,
                        finishedAtMillis = now,
                        durationNanos = managed.durationNanos
                    )
                )
            }
        }

        val action = {
            try {
                if (managed.future?.isDone != true) {
                    val value = task()
                    finishCompleted(managed, value, System.nanoTime() - started)
                }
            } catch (throwable: Throwable) {
                finishFailed(managed, throwable, System.nanoTime() - started)
            } finally {
                timeoutHandle?.cancel()
            }
        }
        val handle = when {
            delayTicks > 0L && async -> scheduler.runAsyncLater(delayTicks, action)
            delayTicks > 0L -> scheduler.runLater(delayTicks, action)
            async -> scheduler.runAsync(action)
            else -> scheduler.runSync(action)
        }
        managed.handle = handle
    }

    private fun <T> finishCompleted(managed: ShimeManagedTask, value: T, durationNanos: Long) {
        val now = System.currentTimeMillis()
        managed.state = ShimeTaskState.COMPLETED
        managed.finishedAtMillis = now
        managed.durationNanos = durationNanos.coerceAtLeast(0L)
        @Suppress("UNCHECKED_CAST")
        val future = managed.future as? CompletableFuture<ShimeTaskResult<T>> ?: return
        future.complete(
            ShimeTaskResult(
                name = managed.name,
                state = ShimeTaskState.COMPLETED,
                value = value,
                startedAtMillis = managed.startedAtMillis,
                finishedAtMillis = now,
                durationNanos = managed.durationNanos
            )
        )
    }

    private fun finishFailed(managed: ShimeManagedTask, throwable: Throwable, durationNanos: Long) {
        val now = System.currentTimeMillis()
        managed.state = ShimeTaskState.FAILED
        managed.finishedAtMillis = now
        managed.durationNanos = durationNanos.coerceAtLeast(0L)
        managed.future?.complete(
            ShimeTaskResult<Any?>(
                name = managed.name,
                state = ShimeTaskState.FAILED,
                error = throwable,
                startedAtMillis = managed.startedAtMillis,
                finishedAtMillis = now,
                durationNanos = managed.durationNanos
            )
        )
    }

    private fun markSkipped(managed: ShimeManagedTask, throwable: Throwable) {
        val now = System.currentTimeMillis()
        managed.state = ShimeTaskState.SKIPPED
        managed.finishedAtMillis = now
        val startedAt = if (managed.startedAtMillis > 0L) managed.startedAtMillis else managed.createdAtMillis
        managed.future?.complete(
            ShimeTaskResult<Any?>(
                name = managed.name,
                state = ShimeTaskState.SKIPPED,
                error = throwable,
                startedAtMillis = startedAt,
                finishedAtMillis = now.coerceAtLeast(startedAt),
                durationNanos = 0L
            )
        )
    }

    private fun rememberRepeating(name: String, group: String, handle: ShimeTaskHandle): ShimeManagedTask {
        val managed = ShimeManagedTask(
            name = name,
            group = group,
            handle = handle
        )
        managed.state = ShimeTaskState.RUNNING
        managed.startedAtMillis = System.currentTimeMillis()
        remember(managed)
        return managed
    }

    private fun remember(managed: ShimeManagedTask) {
        val previous = tasks.put(managed.name, managed)
        previous?.cancel()
        submitted.incrementAndGet()
    }

    private fun requireManaged(name: String): ShimeManagedTask {
        return tasks[name] ?: error("Task '$name' was not registered.")
    }

    private fun pruneCompleted() {
        if (tasks.size <= MAX_RETAINED_TASKS) {
            return
        }
        val finished = tasks.values
            .filter { it.isDone }
            .sortedBy { it.finishedAtMillis }
        val excess = tasks.size - MAX_RETAINED_TASKS
        finished.take(excess.coerceAtLeast(0)).forEach { tasks.remove(it.name, it) }
    }

    private fun requireName(name: String) {
        require(name.isNotBlank()) { "Task name must not be blank." }
    }

    companion object {
        const val SERVICE_NAME = "task"

        /** 完了済みタスクの保持上限。依存関係の解決と監視に必要な分だけ残す。 */
        const val MAX_RETAINED_TASKS = 512
    }
}
