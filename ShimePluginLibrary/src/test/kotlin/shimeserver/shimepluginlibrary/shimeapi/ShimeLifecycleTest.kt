package shimeserver.shimepluginlibrary.shimeapi

import org.junit.jupiter.api.Test
import shimeserver.shimepluginlibrary.shimeexception.ShimeLifecycleException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShimeLifecycleTest {
    @Test
    fun `starts uninitialized`() {
        val lifecycle = ShimeLifecycle()
        assertEquals(ShimeState.UNINITIALIZED, lifecycle.state)
        assertFalse(lifecycle.isReady())
    }

    @Test
    fun `accepts a documented transition chain`() {
        val lifecycle = ShimeLifecycle()
        lifecycle.transition(
            "test-service",
            "initialize",
            setOf(ShimeState.UNINITIALIZED),
            ShimeState.INITIALIZING
        )
        lifecycle.transition(
            "test-service",
            "finish initialization",
            setOf(ShimeState.INITIALIZING),
            ShimeState.READY
        )
        assertTrue(lifecycle.isReady())

        lifecycle.transition("test-service", "shutdown", setOf(ShimeState.READY), ShimeState.STOPPING)
        lifecycle.transition("test-service", "finish shutdown", setOf(ShimeState.STOPPING), ShimeState.STOPPED)
        assertEquals(ShimeState.STOPPED, lifecycle.state)
        assertTrue(lifecycle.state.isTerminal)
    }

    @Test
    fun `rejects a transition from a disallowed state`() {
        val lifecycle = ShimeLifecycle(ShimeState.STOPPED)
        val exception = assertFailsWith<ShimeLifecycleException> {
            lifecycle.transition(
                "test-service",
                "initialize",
                setOf(ShimeState.UNINITIALIZED),
                ShimeState.INITIALIZING
            )
        }
        assertTrue(exception.message!!.contains("test-service"))
        assertTrue(exception.message!!.contains("STOPPED"))
    }

    @Test
    fun `requireReady explains the current state`() {
        val lifecycle = ShimeLifecycle(ShimeState.INITIALIZING)
        val exception = assertFailsWith<ShimeLifecycleException> {
            lifecycle.requireReady("economy", "withdraw")
        }
        assertTrue(exception.message!!.contains("economy"))
        assertTrue(exception.message!!.contains("withdraw"))
        assertTrue(exception.message!!.contains("INITIALIZING"))
    }

    @Test
    fun `force can recover a failed component`() {
        val lifecycle = ShimeLifecycle(ShimeState.FAILED)
        lifecycle.force(ShimeState.UNINITIALIZED)
        assertEquals(ShimeState.UNINITIALIZED, lifecycle.state)
        assertFalse(lifecycle.state.isTerminal)
    }

    @Test
    fun `state usability flags are consistent`() {
        assertTrue(ShimeState.READY.isUsable)
        assertFalse(ShimeState.INITIALIZING.isUsable)
        assertTrue(ShimeState.STOPPED.isTerminal)
        assertTrue(ShimeState.FAILED.isTerminal)
    }
}
