package shimeserver.shimepluginlibrary.shimenms

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShimeNmsReportTest {
    private val version = ShimeNmsVersion(
        minecraftVersion = ShimeMinecraftVersion.PAPER_1_20_6,
        bukkitVersion = "1.20.6-R0.1-SNAPSHOT",
        serverVersion = "git-Paper-151 (MC: 1.20.6)",
        serverImplementation = "org.bukkit.craftbukkit.v1_20_R4.CraftServer",
        nmsPackage = "v1_20_R4"
    )

    @Test
    fun `summary reports the adapter, transport and features`() {
        val report = ShimeNmsReport(
            version = version,
            adapterId = "paper-1.20.6",
            adapterStatus = ShimeNmsAdapterStatus.COMPATIBLE,
            supportedFeatures = setOf(ShimeNmsFeature.VERSION_DETECTION),
            packetTransportAvailable = false,
            warnings = listOf("Packet features are not available.")
        )

        assertTrue(report.isAvailable)
        assertTrue(report.supports(ShimeNmsFeature.VERSION_DETECTION))
        assertFalse(report.supports(ShimeNmsFeature.PACKET_SEND))

        val summary = report.summary()
        assertTrue(summary.contains("paper-1.20.6"))
        assertTrue(summary.contains("COMPATIBLE"))
        assertTrue(summary.contains("Packet transport: unavailable"))
        assertTrue(summary.contains("version detection"))
        assertTrue(summary.contains("Packet features are not available."))
    }

    @Test
    fun `an unavailable report lists no features`() {
        val report = ShimeNmsReport(
            version = version,
            adapterId = null,
            adapterStatus = null,
            supportedFeatures = emptySet(),
            packetTransportAvailable = false,
            warnings = emptyList()
        )

        assertFalse(report.isAvailable)
        assertTrue(report.summary().contains("Adapter: none"))
        assertTrue(report.summary().contains("Features: none"))
    }
}
