package shimeserver.shimepluginlibrary.shimenms

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShimeNmsAdapterRegistryTest {
    private class ShimeTestAdapter(
        override val adapterId: String,
        private val versions: Set<ShimeMinecraftVersion> = setOf(ShimeMinecraftVersion.PAPER_1_20_6),
        private val features: Set<ShimeNmsFeature> = setOf(ShimeNmsFeature.VERSION_DETECTION),
        private val failVersionCheck: Boolean = false
    ) : ShimeNmsAdapter {
        override val supportedMinecraftVersions: Set<ShimeMinecraftVersion> = versions
        private var currentStatus: ShimeNmsAdapterStatus = ShimeNmsAdapterStatus.CREATED

        override fun supportsVersion(version: ShimeNmsVersion): Boolean {
            if (failVersionCheck) {
                throw IllegalStateException("adapter failure")
            }
            return super.supportsVersion(version)
        }

        override fun supports(feature: ShimeNmsFeature): Boolean {
            return currentStatus == ShimeNmsAdapterStatus.COMPATIBLE && feature in features
        }

        override fun initialize(context: ShimeNmsAdapterContext) {
            currentStatus = ShimeNmsAdapterStatus.COMPATIBLE
        }

        override fun shutdown() {
            currentStatus = ShimeNmsAdapterStatus.CLOSED
        }

        override fun status(): ShimeNmsAdapterStatus = currentStatus
    }

    private fun version(major: Int, minor: Int, patch: Int): ShimeNmsVersion {
        return ShimeNmsVersion(
            minecraftVersion = ShimeMinecraftVersion(major, minor, patch),
            bukkitVersion = "$major.$minor.$patch-R0.1-SNAPSHOT",
            serverVersion = "git-Paper (MC: $major.$minor.$patch)",
            serverImplementation = "org.bukkit.craftbukkit.CraftServer",
            nmsPackage = null
        )
    }

    @Test
    fun `registers adapters in order`() {
        val registry = ShimeNmsAdapterRegistry()
        val first = ShimeTestAdapter("first")
        val second = ShimeTestAdapter("second")

        registry.register(first)
        registry.register(second)

        assertEquals(listOf("first", "second"), registry.adapters().map { it.adapterId })
        assertSame(first, registry.get("first"))
        assertNull(registry.get("missing"))
    }

    @Test
    fun `rejects blank and duplicate adapter ids`() {
        val registry = ShimeNmsAdapterRegistry()

        assertFailsWith<IllegalArgumentException> { registry.register(ShimeTestAdapter(" ")) }
        registry.register(ShimeTestAdapter("adapter"))
        assertFailsWith<IllegalArgumentException> { registry.register(ShimeTestAdapter("adapter")) }
    }

    @Test
    fun `replace allows an intentional override`() {
        val registry = ShimeNmsAdapterRegistry()
        val original = ShimeTestAdapter("adapter")
        val replacement = ShimeTestAdapter("adapter")
        registry.register(original)
        registry.register(replacement, replace = true)

        assertSame(replacement, registry.get("adapter"))
    }

    @Test
    fun `finds only compatible adapters`() {
        val registry = ShimeNmsAdapterRegistry()
        val supported = ShimeTestAdapter("supported")
        val unsupported = ShimeTestAdapter("unsupported", versions = setOf(ShimeMinecraftVersion(1, 21, 0)))
        registry.register(supported)
        registry.register(unsupported)

        assertEquals(listOf("supported"), registry.findCompatible(version(1, 20, 6)).map { it.adapterId })
        assertEquals(listOf("unsupported"), registry.findCompatible(version(1, 21, 0)).map { it.adapterId })
        assertTrue(registry.findCompatible(version(1, 19, 4)).isEmpty())
    }

    @Test
    fun `an adapter failure is treated as incompatible`() {
        val registry = ShimeNmsAdapterRegistry()
        registry.register(ShimeTestAdapter("broken", failVersionCheck = true))

        assertTrue(registry.findCompatible(version(1, 20, 6)).isEmpty())
    }

    @Test
    fun `unregister and clear remove adapters`() {
        val registry = ShimeNmsAdapterRegistry()
        registry.register(ShimeTestAdapter("first"))
        registry.register(ShimeTestAdapter("second"))

        assertTrue(registry.unregister("first"))
        assertEquals(false, registry.unregister("first"))
        assertEquals(listOf("second"), registry.adapters().map { it.adapterId })

        registry.clear()
        assertTrue(registry.adapters().isEmpty())
    }
}
