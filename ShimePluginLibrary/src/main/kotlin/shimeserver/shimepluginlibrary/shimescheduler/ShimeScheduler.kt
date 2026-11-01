package shimeserver.shimepluginlibrary.shimescheduler

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * スレッドを意識したスケジューリングファサード。
 *
 * ワールドやインベントリを変更するすべての Bukkit API 呼び出しは、[runSync] または [callSync] を
 * 通じて実行しなければならない。非同期のヘルパーは I/O と CPU 処理専用として提供される。
 */
class ShimeScheduler(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger
) {
    private val tracked: MutableSet<org.bukkit.scheduler.BukkitTask> =
        Collections.newSetFromMap(ConcurrentHashMap())

    fun isPrimaryThread(): Boolean = Bukkit.isPrimaryThread()

    fun runSync(task: () -> Unit): ShimeTaskHandle {
        return track(Bukkit.getScheduler().runTask(plugin, Runnable { execute("sync task", task) }))
    }

    fun runAsync(task: () -> Unit): ShimeTaskHandle {
        return track(Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { execute("async task", task) }))
    }

    fun runLater(delayTicks: Long, task: () -> Unit): ShimeTaskHandle {
        return track(
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable { execute("delayed task", task) },
                requireDelay(delayTicks)
            )
        )
    }

    fun runAsyncLater(delayTicks: Long, task: () -> Unit): ShimeTaskHandle {
        return track(
            Bukkit.getScheduler().runTaskLaterAsynchronously(
                plugin,
                Runnable { execute("delayed async task", task) },
                requireDelay(delayTicks)
            )
        )
    }

    fun runRepeating(delayTicks: Long, periodTicks: Long, task: () -> Unit): ShimeTaskHandle {
        return track(
            Bukkit.getScheduler().runTaskTimer(
                plugin,
                Runnable { execute("repeating task", task) },
                requireDelay(delayTicks),
                requirePeriod(periodTicks)
            )
        )
    }

    fun runAsyncRepeating(delayTicks: Long, periodTicks: Long, task: () -> Unit): ShimeTaskHandle {
        return track(
            Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                Runnable { execute("repeating async task", task) },
                requireDelay(delayTicks),
                requirePeriod(periodTicks)
            )
        )
    }

    /**
     * [supplier] をプライマリスレッドで実行し、戻り値で返される future を完了させる。
     * すでにプライマリスレッド上にいる場合は supplier を即座に実行し、デッドロックを避ける。
     */
    fun <T> callSync(supplier: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (Bukkit.isPrimaryThread()) {
            try {
                future.complete(supplier())
            } catch (throwable: Throwable) {
                logger.report(ShimeLogCategory.TASK, "Synchronous call", throwable)
                future.completeExceptionally(throwable)
            }
            return future
        }
        runSync {
            try {
                future.complete(supplier())
            } catch (throwable: Throwable) {
                logger.report(ShimeLogCategory.TASK, "Synchronous call", throwable)
                future.completeExceptionally(throwable)
            }
        }
        return future
    }

    /** このスケジューラーを通じて作成されたすべてのタスクをキャンセルする。プラグイン停止時に呼び出される。 */
    fun cancelAll() {
        val snapshot = tracked.toList()
        tracked.clear()
        snapshot.forEach { task ->
            try {
                if (!task.isCancelled) {
                    task.cancel()
                }
            } catch (throwable: Throwable) {
                logger.warn(ShimeLogCategory.TASK, "Failed to cancel task ${task.taskId}.", throwable)
            }
        }
    }

    private fun execute(description: String, task: () -> Unit) {
        try {
            task()
        } catch (throwable: Throwable) {
            logger.report(ShimeLogCategory.TASK, description, throwable)
        }
    }

    private fun track(task: org.bukkit.scheduler.BukkitTask): ShimeTaskHandle {
        tracked.add(task)
        return ShimeTaskHandle(task)
    }

    private fun requireDelay(ticks: Long): Long {
        require(ticks >= 0) { "Delay must not be negative." }
        return ticks
    }

    private fun requirePeriod(ticks: Long): Long {
        require(ticks > 0) { "Period must be greater than zero." }
        return ticks
    }
}
