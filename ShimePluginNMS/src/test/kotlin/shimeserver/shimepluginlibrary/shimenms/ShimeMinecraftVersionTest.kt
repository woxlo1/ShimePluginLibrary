package shimeserver.shimepluginlibrary.shimenms

import org.junit.jupiter.api.Test
import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShimeMinecraftVersionTest {
    @Test
    fun `compares versions by each component`() {
        assertTrue(ShimeMinecraftVersion(1, 20, 6) > ShimeMinecraftVersion(1, 20, 5))
        assertTrue(ShimeMinecraftVersion(1, 21, 0) > ShimeMinecraftVersion(1, 20, 99))
        assertTrue(ShimeMinecraftVersion(2, 0, 0) > ShimeMinecraftVersion(1, 99, 99))
        assertEquals(0, ShimeMinecraftVersion(1, 20, 6).compareTo(ShimeMinecraftVersion(1, 20, 6)))
    }

    @Test
    fun `isAtLeast and isBefore are inclusive and exclusive respectively`() {
        val version = ShimeMinecraftVersion(1, 20, 6)
        assertTrue(version.isAtLeast(ShimeMinecraftVersion.PAPER_1_20_6))
        assertTrue(version.isAtLeast(ShimeMinecraftVersion(1, 20, 5)))
        assertFalse(version.isAtLeast(ShimeMinecraftVersion(1, 20, 7)))
        assertTrue(version.isBefore(ShimeMinecraftVersion(1, 21, 0)))
        assertFalse(version.isBefore(ShimeMinecraftVersion.PAPER_1_20_6))
    }

    @Test
    fun `parses Bukkit and server version strings`() {
        assertEquals(
            ShimeMinecraftVersion(1, 20, 6),
            ShimeMinecraftVersion.parseOrNull("1.20.6-R0.1-SNAPSHOT")
        )
        assertEquals(
            ShimeMinecraftVersion(1, 20, 6),
            ShimeMinecraftVersion.parseOrNull("git-Paper-151 (MC: 1.20.6)")
        )
        assertEquals(
            ShimeMinecraftVersion(1, 20, 0),
            ShimeMinecraftVersion.parseOrNull("1.20")
        )
        assertEquals(
            ShimeMinecraftVersion(1, 21, 8),
            ShimeMinecraftVersion.parseOrNull("1.21.8-R0.1-SNAPSHOT")
        )
        assertEquals(
            ShimeMinecraftVersion(1, 21, 8),
            ShimeMinecraftVersion.parseOrNull("git-Paper-60 (MC: 1.21.8)")
        )
    }

    @Test
    fun `exposes the built-in supported versions`() {
        assertTrue(ShimeMinecraftVersion.PAPER_1_20_6 in ShimeMinecraftVersion.BUILT_IN_SUPPORTED_VERSIONS)
        assertTrue(ShimeMinecraftVersion.PAPER_1_21_8 in ShimeMinecraftVersion.BUILT_IN_SUPPORTED_VERSIONS)
        assertEquals(
            setOf(ShimeMinecraftVersion.PAPER_1_20_6, ShimeMinecraftVersion.PAPER_1_21_8),
            ShimeMinecraftVersion.BUILT_IN_SUPPORTED_VERSIONS
        )
    }

    @Test
    fun `returns null for text without a version`() {
        assertNull(ShimeMinecraftVersion.parseOrNull("unknown"))
        assertNull(ShimeMinecraftVersion.parseOrNull(""))
    }

    @Test
    fun `parse throws a validation exception for invalid input`() {
        val exception = assertFailsWith<ShimeValidationException> {
            ShimeMinecraftVersion.parse("not-a-version")
        }
        assertTrue(exception.message!!.contains("not-a-version"))
    }

    @Test
    fun `rejects negative components`() {
        assertFailsWith<IllegalArgumentException> { ShimeMinecraftVersion(-1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { ShimeMinecraftVersion(0, -1, 0) }
        assertFailsWith<IllegalArgumentException> { ShimeMinecraftVersion(0, 0, -1) }
    }

    @Test
    fun `formats as a dotted version`() {
        assertEquals("1.20.6", ShimeMinecraftVersion(1, 20, 6).toString())
    }
}
