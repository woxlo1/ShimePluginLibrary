package shimeserver.shimepluginlibrary.shimecommand

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [ShimeCommandExecutionStats] の検証と平均実行時間の計算を確認する。
 */
class ShimeCommandExecutionStatsTest {
    @Test
    fun `平均実行時間はナノ秒からミリ秒へ変換される`() {
        val stats = ShimeCommandExecutionStats(
            path = "root run",
            executions = 4,
            successes = 3,
            failures = 1,
            totalDurationNanos = 8_000_000,
            lastExecutedMillis = 1_000L
        )

        assertEquals(2.0, stats.averageDurationMillis)
    }

    @Test
    fun `実行回数がゼロの場合の平均はゼロになる`() {
        val stats = ShimeCommandExecutionStats(
            path = "root run",
            executions = 0,
            successes = 0,
            failures = 0,
            totalDurationNanos = 0,
            lastExecutedMillis = 0
        )

        assertEquals(0.0, stats.averageDurationMillis)
    }

    @Test
    fun `負の値は拒否される`() {
        assertFailsWith<IllegalArgumentException> {
            stats(executions = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            stats(successes = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            stats(failures = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            stats(totalDurationNanos = -1)
        }
    }

    private fun stats(
        executions: Long = 1,
        successes: Long = 1,
        failures: Long = 0,
        totalDurationNanos: Long = 1_000_000
    ): ShimeCommandExecutionStats {
        return ShimeCommandExecutionStats(
            path = "root run",
            executions = executions,
            successes = successes,
            failures = failures,
            totalDurationNanos = totalDurationNanos,
            lastExecutedMillis = 1_000L
        )
    }
}
