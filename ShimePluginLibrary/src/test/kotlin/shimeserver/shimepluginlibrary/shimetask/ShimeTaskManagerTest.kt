package shimeserver.shimepluginlibrary.shimetask

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimescheduler.ShimeTaskHandle
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShimeTaskManagerTest {
    @Test
    fun `completed task exposes its value and statistics`() {
        val manager = ShimeTaskManager(scheduler())

        val result = manager.submitSyncFuture("value") { 21 * 2 }.get(2, TimeUnit.SECONDS)

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrThrow())
        assertEquals(ShimeTaskState.COMPLETED, result.state)
        assertTrue(result.durationNanos >= 0L)
        val stats = manager.statistics()
        assertEquals(1L, stats.submitted)
        assertEquals(1L, stats.completed)
        assertNotNull(manager.snapshot("value"))
    }

    @Test
    fun `failed task records the failure and rethrows on demand`() {
        val manager = ShimeTaskManager(scheduler())

        val result = manager.submitSyncFuture<Unit>("failure") {
            throw IllegalStateException("boom")
        }.get(2, TimeUnit.SECONDS)

        assertEquals(ShimeTaskState.FAILED, result.state)
        assertFalse(result.isSuccess)
        assertTrue(result.error is IllegalStateException)
        assertFailsWith<ShimeTaskException> { result.getOrThrow() }
        assertEquals(1L, manager.statistics().failed)
    }

    @Test
    fun `tasks observe successful dependencies`() {
        val manager = ShimeTaskManager(scheduler())

        assertEquals(1, manager.submitSyncFuture("first") { 1 }.get(2, TimeUnit.SECONDS).getOrThrow())
        val dependent = manager.submitSyncFuture(
            name = "second",
            dependsOn = listOf("first")
        ) { 2 }.get(2, TimeUnit.SECONDS)

        assertEquals(ShimeTaskState.COMPLETED, dependent.state)
        assertEquals(2, dependent.getOrThrow())
    }

    @Test
    fun `tasks are skipped when a dependency fails`() {
        val manager = ShimeTaskManager(scheduler())
        manager.submitSyncFuture<Unit>("base") { error("broken") }.get(2, TimeUnit.SECONDS)

        val skipped = manager.submitSyncFuture(
            name = "dependent",
            dependsOn = listOf("base")
        ) { 1 }.get(2, TimeUnit.SECONDS)

        assertEquals(ShimeTaskState.SKIPPED, skipped.state)
        assertTrue(skipped.error is ShimeTaskDependencyException)
        assertEquals(1L, manager.statistics().skipped)
    }

    @Test
    fun `missing and self dependencies are rejected`() {
        val manager = ShimeTaskManager(scheduler())

        assertFailsWith<IllegalArgumentException> {
            manager.submitSyncFuture("orphan", dependsOn = listOf("missing")) { 1 }
        }
        assertFailsWith<IllegalArgumentException> {
            manager.submitSyncFuture("self", dependsOn = listOf("self")) { 1 }
        }
    }

    @Test
    fun `timeout produces a timed out result`() {
        val manager = ShimeTaskManager(scheduler(runDelayedImmediately = true))

        val result = manager.submitSyncFuture(
            name = "slow",
            timeoutTicks = 5
        ) { error("must not run") }.get(2, TimeUnit.SECONDS)

        assertEquals(ShimeTaskState.TIMED_OUT, result.state)
        assertTrue(result.error is ShimeTaskTimeoutException)
        assertEquals(1L, manager.statistics().timedOut)
    }

    @Test
    fun `repeating tasks can be cancelled and reported`() {
        val manager = ShimeTaskManager(scheduler())

        val task = manager.repeat("tick", 1, 20) { }
        assertEquals(ShimeTaskState.RUNNING, task.state)
        assertTrue(manager.get("tick") === task)
        assertTrue(manager.cancel("tick"))
        assertFalse(manager.cancel("tick"))
        assertFalse(manager.cancel("unknown"))
        assertTrue(task.isDone)
        assertEquals(1L, manager.statistics().cancelled)
    }

    @Test
    fun `name and group validation rejects invalid input`() {
        val manager = ShimeTaskManager(scheduler())

        assertFailsWith<IllegalArgumentException> {
            manager.submitSyncFuture("   ") { 1 }
        }
        assertFailsWith<IllegalArgumentException> {
            manager.submitSyncFuture("negative", timeoutTicks = -1) { 1 }
        }
        assertFailsWith<IllegalArgumentException> {
            manager.scheduleFuture("negative-delay", -1) { 1 }
        }
    }

    private fun scheduler(
        runDelayedImmediately: Boolean = false
    ): ShimeScheduler {
        val scheduler = mock(ShimeScheduler::class.java)
        Mockito.`when`(scheduler.runSync(anyAction())).thenAnswer { invocation ->
            val action = invocation.getArgument<() -> Unit>(0)
            action()
            ShimeTaskHandle(null)
        }
        Mockito.`when`(scheduler.runAsync(anyAction())).thenAnswer { invocation ->
            val action = invocation.getArgument<() -> Unit>(0)
            action()
            ShimeTaskHandle(null)
        }
        Mockito.`when`(scheduler.runLater(Mockito.anyLong(), anyAction())).thenAnswer { invocation ->
            if (runDelayedImmediately) {
                invocation.getArgument<() -> Unit>(1).invoke()
            }
            ShimeTaskHandle(null)
        }
        Mockito.`when`(scheduler.runAsyncLater(Mockito.anyLong(), anyAction())).thenAnswer { invocation ->
            if (runDelayedImmediately) {
                invocation.getArgument<() -> Unit>(1).invoke()
            }
            ShimeTaskHandle(null)
        }
        Mockito.`when`(scheduler.runRepeating(Mockito.anyLong(), Mockito.anyLong(), anyAction()))
            .thenReturn(ShimeTaskHandle(null))
        Mockito.`when`(scheduler.runAsyncRepeating(Mockito.anyLong(), Mockito.anyLong(), anyAction()))
            .thenReturn(ShimeTaskHandle(null))
        return scheduler
    }

    /**
     * Kotlin の非 null 関数型引数に null を渡さずに Mockito のマッチャーを登録する。
     */
    private fun anyAction(): () -> Unit = Mockito.argThat<() -> Unit> { true } ?: {}
}
