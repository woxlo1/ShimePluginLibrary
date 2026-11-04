package shimeserver.shimepluginlibrary.shimenms

import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import shimeserver.shimepluginlibrary.shimepacket.ShimePacketManager
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShimePaper1218AdapterTest {
    private fun version(
        minecraftVersion: ShimeMinecraftVersion?,
        bukkitVersion: String,
        serverVersion: String,
        serverImplementation: String,
        nmsPackage: String?
    ): ShimeNmsVersion {
        return ShimeNmsVersion(
            minecraftVersion = minecraftVersion,
            bukkitVersion = bukkitVersion,
            serverVersion = serverVersion,
            serverImplementation = serverImplementation,
            nmsPackage = nmsPackage
        )
    }

    private fun <T> withContext(version: ShimeNmsVersion, block: (ShimeNmsAdapterContext) -> T): T {
        val logger = ShimeLogger(Logger.getLogger("ShimePaper1218AdapterTest"))
        val packets = ShimePacketManager(logger)
        try {
            return block(
                ShimeNmsAdapterContext(
                    plugin = Mockito.mock(JavaPlugin::class.java),
                    logger = logger,
                    version = version,
                    reflection = ShimeReflection(),
                    packets = packets
                )
            )
        } finally {
            packets.close()
        }
    }

    private val supported = version(
        minecraftVersion = ShimeMinecraftVersion.PAPER_1_21_8,
        bukkitVersion = "1.21.8-R0.1-SNAPSHOT",
        serverVersion = "git-Paper-60 (MC: 1.21.8)",
        serverImplementation = "org.bukkit.craftbukkit.v1_21_R5.CraftServer",
        nmsPackage = "v1_21_R5"
    )

    @Test
    fun `declares support for 1_21_8 only`() {
        val adapter = ShimePaper1218Adapter()

        assertEquals("paper-1.21.8", adapter.adapterId)
        assertEquals(setOf(ShimeMinecraftVersion.PAPER_1_21_8), adapter.supportedMinecraftVersions)
        assertEquals(ShimeNmsAdapterStatus.CREATED, adapter.status())
    }

    @Test
    fun `claims no feature before initialization`() {
        val adapter = ShimePaper1218Adapter()

        assertFalse(adapter.supports(ShimeNmsFeature.VERSION_DETECTION))
        assertFalse(adapter.supports(ShimeNmsFeature.PACKET_SEND))
        assertEquals(null, adapter.packetTransport())
    }

    @Test
    fun `initializes as compatible and reports only version detection`() {
        val adapter = ShimePaper1218Adapter()

        withContext(supported) { context ->
            adapter.initialize(context)
        }

        assertEquals(ShimeNmsAdapterStatus.COMPATIBLE, adapter.status())
        assertTrue(adapter.supports(ShimeNmsFeature.VERSION_DETECTION))
        assertFalse(adapter.supports(ShimeNmsFeature.PACKET_SEND))
        assertEquals(null, adapter.packetTransport())
    }

    @Test
    fun `reports an unsupported version without claiming features`() {
        val adapter = ShimePaper1218Adapter()
        val other = version(
            minecraftVersion = ShimeMinecraftVersion.PAPER_1_20_6,
            bukkitVersion = "1.20.6-R0.1-SNAPSHOT",
            serverVersion = "git-Paper-151 (MC: 1.20.6)",
            serverImplementation = "org.bukkit.craftbukkit.v1_20_R4.CraftServer",
            nmsPackage = "v1_20_R4"
        )

        withContext(other) { context ->
            adapter.initialize(context)
        }

        assertEquals(ShimeNmsAdapterStatus.UNSUPPORTED_VERSION, adapter.status())
        assertFalse(adapter.supports(ShimeNmsFeature.VERSION_DETECTION))
    }

    @Test
    fun `supports only the matching Minecraft version`() {
        val adapter = ShimePaper1218Adapter()
        val other = version(
            minecraftVersion = ShimeMinecraftVersion.PAPER_1_20_6,
            bukkitVersion = "1.20.6-R0.1-SNAPSHOT",
            serverVersion = "git-Paper-151 (MC: 1.20.6)",
            serverImplementation = "org.bukkit.craftbukkit.v1_20_R4.CraftServer",
            nmsPackage = "v1_20_R4"
        )

        assertTrue(adapter.supportsVersion(supported))
        assertFalse(adapter.supportsVersion(other))
        assertFalse(
            adapter.supportsVersion(
                version(
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
    fun `the registry selects the adapter for the detected version`() {
        val registry = ShimeNmsAdapterRegistry()
        registry.register(ShimePaper1206Adapter())
        registry.register(ShimePaper1218Adapter())

        assertEquals(
            listOf("paper-1.21.8"),
            registry.findCompatible(supported).map { it.adapterId }
        )
        assertEquals(
            listOf("paper-1.20.6"),
            registry.findCompatible(
                version(
                    minecraftVersion = ShimeMinecraftVersion.PAPER_1_20_6,
                    bukkitVersion = "1.20.6-R0.1-SNAPSHOT",
                    serverVersion = "git-Paper-151 (MC: 1.20.6)",
                    serverImplementation = "org.bukkit.craftbukkit.v1_20_R4.CraftServer",
                    nmsPackage = "v1_20_R4"
                )
            ).map { it.adapterId }
        )
    }

    @Test
    fun `shutdown is idempotent and closes the adapter`() {
        val adapter = ShimePaper1218Adapter()
        adapter.shutdown()
        adapter.shutdown()

        assertEquals(ShimeNmsAdapterStatus.CLOSED, adapter.status())
        assertFalse(adapter.supports(ShimeNmsFeature.VERSION_DETECTION))
    }
}
