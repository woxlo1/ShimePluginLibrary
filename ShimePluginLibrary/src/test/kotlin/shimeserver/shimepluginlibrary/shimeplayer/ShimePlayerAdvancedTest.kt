package shimeserver.shimepluginlibrary.shimeplayer

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShimePlayerAdvancedTest {
    @Test
    fun `login records validate timestamps and compute durations`() {
        val record = ShimeLoginRecord(joinMillis = 1_000L)

        assertTrue(record.isActive)
        assertEquals(500L, record.durationMillis(nowMillis = 1_500L))
        assertFalse(record.copy(quitMillis = 2_000L).isActive)
        assertEquals(1_000L, record.copy(quitMillis = 2_000L).durationMillis())

        assertFailsWith<IllegalArgumentException> { ShimeLoginRecord(0L) }
        assertFailsWith<IllegalArgumentException> { ShimeLoginRecord(2_000L, 1_000L) }
    }

    @Test
    fun `profile caps login history and closes the active session`() {
        val profile = ShimePlayerProfile(UUID.randomUUID(), "ShimePlayer")
        repeat(ShimePlayerProfile.MAX_LOGIN_HISTORY + 10) { index ->
            profile.addLogin(ShimeLoginRecord(1_000L + index))
        }

        assertEquals(ShimePlayerProfile.MAX_LOGIN_HISTORY, profile.loginHistory.size)
        val latest = profile.recentLogins(1).single()
        assertTrue(latest.isActive)

        profile.loginHistory.clear()
        profile.addLogin(ShimeLoginRecord(5_000L))
        val closed = profile.closeActiveLogin(quitMillis = 5_500L)
        assertNotNull(closed)
        assertFalse(closed.isActive)
        assertEquals(5_500L, closed.quitMillis)
        assertNull(profile.closeActiveLogin())
        assertFailsWith<IllegalArgumentException> { profile.recentLogins(-1) }
    }

    @Test
    fun `profile copy isolates mutable state`() {
        val profile = ShimePlayerProfile(UUID.randomUUID(), "Original")
        profile.setMetadata("rank", "admin")
        profile.addLogin(ShimeLoginRecord(1_000L))

        val copy = profile.copy()
        copy.name = "Copy"
        copy.setMetadata("rank", "member")
        copy.loginHistory.clear()

        assertEquals("Original", profile.name)
        assertEquals("admin", profile.metadata("rank"))
        assertEquals(1, profile.loginHistory.size)
    }

    @Test
    fun `session closes registered resources in reverse order and clears values`() {
        val session = ShimePlayerSession(UUID.randomUUID(), 1_000L)
        val order = ArrayList<String>()
        session.register { order += "first" }
        session.register { order += "second" }
        session.put("key", "value")

        assertEquals("value", session.get<String>("key"))
        assertEquals(500L, session.elapsedMillis(nowMillis = 1_500L))
        session.close()

        assertTrue(session.isClosed)
        assertEquals(listOf("second", "first"), order)
        assertNull(session.get<String>("key"))
        assertFailsWith<IllegalStateException> { session.put("later", "value") }
    }

    @Test
    fun `session aggregates resource close failures`() {
        val session = ShimePlayerSession(UUID.randomUUID())
        val order = ArrayList<String>()
        session.register {
            order += "first"
            throw IllegalStateException("first failure")
        }
        session.register {
            order += "second"
            throw IllegalStateException("second failure")
        }

        val failure = assertFailsWith<IllegalStateException> { session.close() }

        assertEquals(listOf("second", "first"), order)
        assertEquals(1, failure.cause?.suppressed?.size)
        assertTrue(session.isClosed)
        session.close()
    }

    @Test
    fun `profile codec round trips login history and metadata`() {
        val uuid = UUID.randomUUID()
        val profile = ShimePlayerProfile(
            uuid = uuid,
            name = "RoundTrip",
            firstJoinMillis = 1_000L,
            lastJoinMillis = 2_000L,
            lastQuitMillis = 3_000L,
            playTimeMillis = 4_000L,
            locale = "ja_JP",
            sessionCount = 2
        )
        profile.setMetadata("rank", "admin")
        profile.addLogin(ShimeLoginRecord(1_000L, 2_000L))
        profile.addLogin(ShimeLoginRecord(3_000L))

        val decoded = ShimePlayerProfileCodec.decode(ShimePlayerProfileCodec.encode(profile))

        assertEquals(uuid, decoded.uuid)
        assertEquals("RoundTrip", decoded.name)
        assertEquals(1_000L, decoded.firstJoinMillis)
        assertEquals(4_000L, decoded.playTimeMillis)
        assertEquals("ja_JP", decoded.locale)
        assertEquals(2, decoded.sessionCount)
        assertEquals("admin", decoded.metadata("rank"))
        assertEquals(2, decoded.loginHistory.size)
        assertTrue(decoded.loginHistory.last().isActive)
    }

    @Test
    fun `statistics compute average session length`() {
        val statistics = ShimePlayerStatistics(
            uuid = UUID.randomUUID(),
            name = "Stats",
            sessionCount = 2,
            totalPlayTimeMillis = 1_001L,
            lastJoinMillis = 1_000L,
            lastQuitMillis = 2_000L
        )

        assertEquals(500L, statistics.averageSessionMillis)
        assertEquals(
            0L,
            statistics.copy(sessionCount = 0, totalPlayTimeMillis = 0L).averageSessionMillis
        )
    }
}
