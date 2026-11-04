package shimeserver.shimepluginlibrary.shimeevent

import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.util.concurrent.atomic.AtomicLong

/**
 * [ShimeEventManager.subscribe] が返す購読ハンドル。
 *
 * 購読は明示的に [unregister] するか、マネージャーの停止時に解除されます。
 * 統計は購読ごとに集計され、フィルターやハンドラーで発生した例外も記録されます。
 */
class ShimeEventSubscription internal constructor(
    val id: String,
    val eventType: Class<out Event>,
    val priority: EventPriority,
    val ignoreCancelled: Boolean,
    internal val listener: Listener,
    private val onUnregister: (ShimeEventSubscription) -> Unit
) {
    private val invocations = AtomicLong()
    private val failures = AtomicLong()
    private val filtered = AtomicLong()
    private val totalDurationNanos = AtomicLong()

    @Volatile
    private var active: Boolean = true

    val isActive: Boolean
        get() = active

    val invocationCount: Long
        get() = invocations.get()

    val failureCount: Long
        get() = failures.get()

    val filteredCount: Long
        get() = filtered.get()

    val totalNanos: Long
        get() = totalDurationNanos.get()

    internal fun recordHandled(durationNanos: Long, failed: Boolean) {
        invocations.incrementAndGet()
        if (failed) {
            failures.incrementAndGet()
        }
        totalDurationNanos.addAndGet(durationNanos.coerceAtLeast(0L))
    }

    internal fun recordFiltered() {
        filtered.incrementAndGet()
    }

    /** 購読を解除する。二重解除は false を返す。 */
    fun unregister(): Boolean {
        if (!active) {
            return false
        }
        active = false
        onUnregister(this)
        return true
    }

    /** 監視用スナップショット。 */
    fun snapshot(): ShimeEventSubscriptionStats {
        return ShimeEventSubscriptionStats(
            id = id,
            eventType = eventType.name,
            priority = priority,
            active = active,
            invocations = invocations.get(),
            filtered = filtered.get(),
            failures = failures.get(),
            totalDurationNanos = totalDurationNanos.get()
        )
    }
}

/** 購読 1 件分の統計。 */
data class ShimeEventSubscriptionStats(
    val id: String,
    val eventType: String,
    val priority: EventPriority,
    val active: Boolean,
    val invocations: Long,
    val filtered: Long,
    val failures: Long,
    val totalDurationNanos: Long
) {
    init {
        require(id.isNotBlank()) { "Subscription id must not be blank." }
        require(invocations >= 0L) { "Invocation count must not be negative." }
        require(filtered >= 0L) { "Filtered count must not be negative." }
        require(failures >= 0L) { "Failure count must not be negative." }
        require(totalDurationNanos >= 0L) { "Total duration must not be negative." }
    }

    val averageDurationNanos: Long
        get() = if (invocations == 0L) 0L else totalDurationNanos / invocations
}
