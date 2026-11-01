package shimeserver.shimepluginlibrary.shimetask

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimescheduler.ShimeTaskHandle
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * [ShimeScheduler] 上に構築された名前付きタスクレジストリ。
 *
 * 名前はマネージャーごとに一意である。グループにより、無関係なタスクに触れることなく
 * ある機能の処理をキャンセルできる。すべてのタスクは停止時にキャンセルされる。
 */
class ShimeTaskManager(
    private val scheduler: ShimeScheduler
) : ShimeAbstractService(SERVICE_NAME) {
    private val tasks = ConcurrentHashMap<String, ShimeManagedTask>()
    private val sequence = AtomicLong()
    private val submitted = AtomicLong()
    private val cancelled = AtomicLong()

    override fun onInitialize(context: ShimeContext) {
        // スケジューラーはサービス初期化前にライブラリによって作成される。
    }

    override fun onShutdown() {
        cancelAll()
    }

    fun submitSync(name: String, group: String = "default", task: () -> Unit): ShimeManagedTask {
        requireName(name)
        val handle = scheduler.runSync(task)
        return remember(name, group, handle)
    }

    fun submitAsync(name: String, group: String = "default", task: () -> Unit): ShimeManagedTask {
        requireName(name)
        val handle = scheduler.runAsync(task)
        return remember(name, group, handle)
    }

    fun schedule(name: String, delayTicks: Long, group: String = "default", task: () -> Unit): ShimeManagedTask {
        requireName(name)
        val handle = scheduler.runLater(delayTicks, task)
        return remember(name, group, handle)
    }

    fun scheduleAsync(name: String, delayTicks: Long, group: String = "default", task: () -> Unit): ShimeManagedTask {
        requireName(name)
        val handle = scheduler.runAsyncLater(delayTicks, task)
        return remember(name, group, handle)
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
        return remember(name, group, handle)
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
        return remember(name, group, handle)
    }

    fun cancel(name: String): Boolean {
        val task = tasks.remove(name) ?: return false
        task.handle.cancel()
        cancelled.incrementAndGet()
        return true
    }

    fun cancelGroup(group: String): Int {
        val matching = tasks.values.filter { it.group == group }
        matching.forEach { tasks.remove(it.name) }
        matching.forEach { it.handle.cancel() }
        cancelled.addAndGet(matching.size.toLong())
        return matching.size
    }

    fun cancelAll() {
        val snapshot = tasks.values.toList()
        tasks.clear()
        snapshot.forEach { it.handle.cancel() }
        cancelled.addAndGet(snapshot.size.toLong())
    }

    fun get(name: String): ShimeManagedTask? = tasks[name]?.takeUnless { it.isCancelled }

    fun activeTasks(): List<ShimeManagedTask> = tasks.values.filterNot { it.isCancelled }

    fun statistics(): ShimeTaskStatistics {
        activeTasks().forEach { if (it.isCancelled) tasks.remove(it.name) }
        return ShimeTaskStatistics(
            submitted = submitted.get(),
            cancelled = cancelled.get(),
            active = tasks.size
        )
    }

    private fun remember(name: String, group: String, handle: ShimeTaskHandle): ShimeManagedTask {
        val managed = ShimeManagedTask(name, group, handle)
        val previous = tasks.put(name, managed)
        previous?.handle?.cancel()
        submitted.incrementAndGet()
        return managed
    }

    private fun requireName(name: String) {
        require(name.isNotBlank()) { "Task name must not be blank." }
    }

    companion object {
        const val SERVICE_NAME = "task"
    }
}

/** 診断用のタスクカウンターのスナップショット。 */
data class ShimeTaskStatistics(
    val submitted: Long,
    val cancelled: Long,
    val active: Int
)
