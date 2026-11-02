package shimeserver.shimepluginlibrary.shimecommand

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ShimeCommandAuditLog] の上限管理、取得順、購読解除を検証する。
 */
class ShimeCommandAuditLogTest {
    @Test
    fun `記録したエントリは追加順に保持される`() {
        val log = ShimeCommandAuditLog()

        log.record(entry("first"))
        log.record(entry("second"))

        assertEquals(listOf("first", "second"), log.entries().map { it.label })
        assertEquals(2, log.size())
    }

    @Test
    fun `上限を超えると古いエントリから破棄される`() {
        val log = ShimeCommandAuditLog(maxEntries = 2)

        log.record(entry("first"))
        log.record(entry("second"))
        log.record(entry("third"))

        assertEquals(listOf("second", "third"), log.entries().map { it.label })
    }

    @Test
    fun `recent は新しい順に件数を制限して返す`() {
        val log = ShimeCommandAuditLog()
        log.record(entry("first"))
        log.record(entry("second"))
        log.record(entry("third"))

        assertEquals(listOf("third", "second"), log.recent(2).map { it.label })
        assertEquals(emptyList(), log.recent(0))
        assertEquals(listOf("third", "second", "first"), log.recent(10).map { it.label })
    }

    @Test
    fun `不正な上限と負の件数は拒否される`() {
        assertFailsWith<IllegalArgumentException> { ShimeCommandAuditLog(0) }
        assertFailsWith<IllegalArgumentException> { ShimeCommandAuditLog(-1) }
        assertFailsWith<IllegalArgumentException> { ShimeCommandAuditLog().recent(-1) }
    }

    @Test
    fun `リスナーの例外は他のリスナーと記録を妨げない`() {
        val log = ShimeCommandAuditLog()
        val received = mutableListOf<String>()
        log.addListener { throw IllegalStateException("listener failure") }
        log.addListener { received += it.label }

        log.record(entry("safe"))

        assertEquals(listOf("safe"), received)
        assertEquals(1, log.size())
    }

    @Test
    fun `購読を解除すると以降の通知を受け取らない`() {
        val log = ShimeCommandAuditLog()
        val received = mutableListOf<String>()
        val subscription = log.addListener { received += it.label }

        log.record(entry("before"))
        subscription.cancel()
        subscription.cancel()
        log.record(entry("after"))

        assertEquals(listOf("before"), received)
        assertFalse(subscription.isActive)
    }

    @Test
    fun `removeListener で登録済みリスナーを解除できる`() {
        val log = ShimeCommandAuditLog()
        val received = mutableListOf<String>()
        val listener: (ShimeCommandAuditEntry) -> Unit = { received += it.label }
        log.addListener(listener)

        assertTrue(log.removeListener(listener))
        log.record(entry("ignored"))

        assertTrue(received.isEmpty())
        assertFalse(log.removeListener(listener))
    }

    @Test
    fun `clear は保持しているエントリだけを削除する`() {
        val log = ShimeCommandAuditLog()
        log.record(entry("first"))

        log.clear()

        assertEquals(0, log.size())
        assertTrue(log.entries().isEmpty())
    }

    private fun entry(label: String): ShimeCommandAuditEntry {
        return ShimeCommandAuditEntry(
            timestampMillis = 1_000L,
            senderName = "tester",
            senderId = "console:tester",
            commandPath = "root $label",
            label = label,
            arguments = listOf(label),
            resultType = "Success",
            success = true,
            durationNanos = 2_000_000L
        )
    }
}
