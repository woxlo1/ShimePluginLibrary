package shimeserver.shimepluginlibrary.shimevalidation

import org.junit.jupiter.api.Test
import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ShimeValidationTest {
    @Test
    fun `requireNotNull returns the original value`() {
        val value = "ready"
        assertSame(value, ShimeValidation.requireNotNull(value, "value"))
    }

    @Test
    fun `requireNotNull names the missing field`() {
        val exception = assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireNotNull(null, "database.host")
        }
        assertEquals("'database.host' must not be null.", exception.message)
    }

    @Test
    fun `requireNonBlank rejects whitespace`() {
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireNonBlank("   ", "name")
        }
    }

    @Test
    fun `requireLength enforces both bounds`() {
        assertEquals("abc", ShimeValidation.requireLength("abc", "name", 3, 3))
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireLength("ab", "name", 3, 5)
        }
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireLength("abcdef", "name", 3, 5)
        }
    }

    @Test
    fun `requireInRange accepts inclusive bounds`() {
        assertEquals(1, ShimeValidation.requireInRange(1, "port", 1, 65_535))
        assertEquals(65_535, ShimeValidation.requireInRange(65_535, "port", 1, 65_535))
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireInRange(0, "port", 1, 65_535)
        }
    }

    @Test
    fun `requirePositive distinguishes zero from positive`() {
        assertEquals(1, ShimeValidation.requirePositive(1, "count"))
        assertEquals(0, ShimeValidation.requirePositive(0, "count", allowZero = true))
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requirePositive(0, "count")
        }
    }

    @Test
    fun `requireSafeKey allows documented characters only`() {
        assertEquals("shime:key_1.0", ShimeValidation.requireSafeKey("shime:key_1.0", "key"))
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireSafeKey("bad key", "key")
        }
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireSafeKey("a".repeat(129), "key")
        }
    }

    @Test
    fun `requireSafeFileName rejects traversal names`() {
        assertEquals("config.yml", ShimeValidation.requireSafeFileName("config.yml", "file"))
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireSafeFileName("..", "file")
        }
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireSafeFileName("../config.yml", "file")
        }
    }

    @Test
    fun `requireUuid parses canonical values`() {
        val uuid = UUID.randomUUID()
        assertEquals(uuid, ShimeValidation.requireUuid(uuid.toString(), "player"))
        assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireUuid("not-a-uuid", "player")
        }
    }

    @Test
    fun `requireTrue reports the supplied message`() {
        ShimeValidation.requireTrue(true, "unreachable")
        val exception = assertFailsWith<ShimeValidationException> {
            ShimeValidation.requireTrue(false, "must be true")
        }
        assertEquals("must be true", exception.message)
    }
}
