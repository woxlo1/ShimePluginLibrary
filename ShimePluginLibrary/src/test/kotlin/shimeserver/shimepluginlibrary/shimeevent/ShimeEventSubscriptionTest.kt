package shimeserver.shimepluginlibrary.shimeevent

import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShimeEventSubscriptionTest {
    @Test
    fun `subscription aggregates invocation and failure statistics`() {
        val subscription = subscription()

        subscription.recordHandled(1_000L, failed = false)
        subscription.recordHandled(3_000L, failed = true)
        subscription.recordFiltered()

        assertEquals(2L, subscription.invocationCount)
        assertEquals(1L, subscription.failureCount)
        assertEquals(1L, subscription.filteredCount)
        assertEquals(4_000L, subscription.totalNanos)

        val snapshot = subscription.snapshot()
        assertEquals("subscription-test", snapshot.id)
        assertEquals(PlayerJoinEvent::class.java.name, snapshot.eventType)
        assertEquals(EventPriority.HIGH, snapshot.priority)
        assertTrue(snapshot.active)
        assertEquals(2_000L, snapshot.averageDurationNanos)
    }

    @Test
    fun `unregister is idempotent and notifies once`() {
        val notified = AtomicBoolean(false)
        val subscription = subscription(onUnregister = { notified.set(true) })

        assertTrue(subscription.unregister())
        assertFalse(subscription.unregister())
        assertFalse(subscription.isActive)
        assertTrue(notified.get())
        assertFalse(subscription.snapshot().active)
    }

    @Test
    fun `statistics reject negative counters`() {
        assertFailsWith<IllegalArgumentException> {
            ShimeEventSubscriptionStats(
                id = "invalid",
                eventType = "event",
                priority = EventPriority.NORMAL,
                active = true,
                invocations = -1L,
                filtered = 0L,
                failures = 0L,
                totalDurationNanos = 0L
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ShimeEventSubscriptionStats(
                id = "invalid",
                eventType = "event",
                priority = EventPriority.NORMAL,
                active = true,
                invocations = 0L,
                filtered = -1L,
                failures = 0L,
                totalDurationNanos = 0L
            )
        }
    }

    private fun subscription(
        onUnregister: (ShimeEventSubscription) -> Unit = {}
    ): ShimeEventSubscription {
        return ShimeEventSubscription(
            id = "subscription-test",
            eventType = PlayerJoinEvent::class.java,
            priority = EventPriority.HIGH,
            ignoreCancelled = true,
            listener = object : Listener {},
            onUnregister = onUnregister
        )
    }
}
