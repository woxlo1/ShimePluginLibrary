package shimeserver.shimepluginlibrary.shimescheduler

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

/** スケジュールされた Bukkit タスクを包む安全なハンドル。 */
class ShimeTaskHandle internal constructor(private val task: BukkitTask?) {
    val taskId: Int
        get() = task?.taskId ?: -1

    val isCancelled: Boolean
        get() = task == null || task.isCancelled

    fun cancel() {
        task?.cancel()
    }
}
