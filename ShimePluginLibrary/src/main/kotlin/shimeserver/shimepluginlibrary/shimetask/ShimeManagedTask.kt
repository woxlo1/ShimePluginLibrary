package shimeserver.shimepluginlibrary.shimetask

import shimeserver.shimepluginlibrary.shimescheduler.ShimeTaskHandle

/** [ShimeTaskManager] によって管理される名前付きタスク。 */
data class ShimeManagedTask(
    val name: String,
    val group: String,
    val handle: ShimeTaskHandle,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val isCancelled: Boolean
        get() = handle.isCancelled
}
