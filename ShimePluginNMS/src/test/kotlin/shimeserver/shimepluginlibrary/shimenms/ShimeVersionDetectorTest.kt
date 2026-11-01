package shimeserver.shimepluginlibrary.shimenms

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShimeVersionDetectorTest {
    @Test
    fun `detects Paper 1_20_6 and its NMS package`() {
        val version = ShimeVersionDetector.detect(
            bukkitVersion = "1.20.6-R0.1-SNAPSHOT",
            serverVersion = "git-Paper-151 (MC: 1.20.6)",
            serverClassName = "org.bukkit.craftbukkit.v1_20_R4.CraftServer"
        )

        assertEquals(ShimeMinecraftVersion.PAPER_1_20_6, version.minecraftVersion)
        assertEquals("v1_20_R4", version.nmsPackage)
        assertEquals("net.minecraft.server.v1_20_R4", version.nmsPackageName)
        assertTrue(version.isDetected)
        assertTrue(version.isSupported)
    }

    @Test
    fun `falls back to the server version string`() {
        val version = ShimeVersionDetector.detect(
            bukkitVersion = "unknown",
            serverVersion = "git-Paper-151 (MC: 1.20.6)",
            serverClassName = "com.example.Server"
        )

        assertEquals(ShimeMinecraftVersion.PAPER_1_20_6, version.minecraftVersion)
        assertNull(version.nmsPackage)
        assertNull(version.nmsPackageName)
    }

    @Test
    fun `reports an undetected version honestly`() {
        val version = ShimeVersionDetector.detect(
            bukkitVersion = "unknown",
            serverVersion = "custom build",
            serverClassName = "com.example.Server"
        )

        assertNull(version.minecraftVersion)
        assertFalse(version.isDetected)
        assertFalse(version.isSupported)
        assertTrue(version.summary().contains("unknown"))
    }

    @Test
    fun `does not mark another version as supported by the built-in adapter`() {
        val version = ShimeVersionDetector.detect(
            bukkitVersion = "1.21-R0.1-SNAPSHOT",
            serverVersion = "git-Paper-1 (MC: 1.21)",
            serverClassName = "org.bukkit.craftbukkit.v1_21_R1.CraftServer"
        )

        assertEquals(ShimeMinecraftVersion(1, 21, 0), version.minecraftVersion)
        assertFalse(version.isSupported)
        assertEquals("v1_21_R1", version.nmsPackage)
    }
}
