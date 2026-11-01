package shimeserver.shimepluginlibrary.shimenms

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShimePaper1206AdapterTest {
    @Test
    fun `declares support for the initial target version only`() {
        val adapter = ShimePaper1206Adapter()

        assertEquals("paper-1.20.6", adapter.adapterId)
        assertEquals(setOf(ShimeMinecraftVersion.PAPER_1_20_6), adapter.supportedMinecraftVersions)
        assertEquals(ShimeNmsAdapterStatus.CREATED, adapter.status())
    }

    @Test
    fun `claims no feature before initialization`() {
        val adapter = ShimePaper1206Adapter()

        assertFalse(adapter.supports(ShimeNmsFeature.VERSION_DETECTION))
        assertFalse(adapter.supports(ShimeNmsFeature.PACKET_SEND))
        assertEquals(null, adapter.packetTransport())
    }

    @Test
    fun `supportsVersion checks the detected Minecraft version`() {
        val adapter = ShimePaper1206Adapter()
        val supported = ShimeNmsVersion(
            minecraftVersion = ShimeMinecraftVersion.PAPER_1_20_6,
            bukkitVersion = "1.20.6-R0.1-SNAPSHOT",
            serverVersion = "git-Paper-151 (MC: 1.20.6)",
            serverImplementation = "org.bukkit.craftbukkit.v1_20_R4.CraftServer",
            nmsPackage = "v1_20_R4"
        )
        val unsupported = ShimeNmsVersion(
            minecraftVersion = ShimeMinecraftVersion(1, 21, 0),
            bukkitVersion = "1.21-R0.1-SNAPSHOT",
            serverVersion = "git-Paper-1 (MC: 1.21)",
            serverImplementation = "org.bukkit.craftbukkit.v1_21_R1.CraftServer",
            nmsPackage = "v1_21_R1"
        )

        assertTrue(adapter.supportsVersion(supported))
        assertFalse(adapter.supportsVersion(unsupported))
        assertFalse(
            adapter.supportsVersion(
                ShimeNmsVersion(
                    minecraftVersion = null,
                    bukkitVersion = "unknown",
                    serverVersion = "unknown",
                    serverImplementation = "unknown",
                    nmsPackage = null
                )
            )
        )
    }

    @Test
    fun `shutdown is idempotent and closes the adapter`() {
        val adapter = ShimePaper1206Adapter()
        adapter.shutdown()
        adapter.shutdown()

        assertEquals(ShimeNmsAdapterStatus.CLOSED, adapter.status())
        assertFalse(adapter.supports(ShimeNmsFeature.VERSION_DETECTION))
    }
}
