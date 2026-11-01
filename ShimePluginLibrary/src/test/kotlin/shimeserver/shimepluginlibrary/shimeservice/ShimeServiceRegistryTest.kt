package shimeserver.shimepluginlibrary.shimeservice

import org.junit.jupiter.api.Test
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeapi.ShimeState
import shimeserver.shimepluginlibrary.shimeexception.ShimeServiceException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class ShimeServiceRegistryTest {
    private class ShimeTestService(
        override val serviceName: String,
        override val servicePriority: Int = 0,
        override val serviceDependencies: Set<String> = emptySet()
    ) : ShimeService {
        override var state: ShimeState = ShimeState.UNINITIALIZED
            private set

        var initialized = false
        var shutDown = false

        override fun initialize(context: ShimeContext) {
            initialized = true
            state = ShimeState.READY
        }

        override fun shutdown() {
            shutDown = true
            state = ShimeState.STOPPED
        }

        fun markReady() {
            state = ShimeState.READY
        }
    }

    private fun registry(): ShimeServiceRegistry {
        return ShimeServiceRegistry(ShimeLogger(Logger.getLogger("ShimeServiceRegistryTest")))
    }

    @Test
    fun `registers and finds services by name and type`() {
        val registry = registry()
        val service = ShimeTestService("economy")
        registry.register(service)

        assertSame(service, registry.getByName("economy"))
        assertSame(service, registry.get(ShimeService::class.java))
        assertSame(service, registry.require(ShimeTestService::class.java))
        assertEquals(listOf("economy"), registry.names())
        assertEquals(mapOf("economy" to ShimeState.UNINITIALIZED), registry.snapshot())
    }

    @Test
    fun `rejects blank and duplicate names`() {
        val registry = registry()
        assertFailsWith<ShimeServiceException> {
            registry.register(ShimeTestService(" "))
        }

        registry.register(ShimeTestService("economy"))
        assertFailsWith<ShimeServiceException> {
            registry.register(ShimeTestService("economy"))
        }
    }

    @Test
    fun `replace is allowed only before initialization`() {
        val registry = registry()
        val original = ShimeTestService("economy")
        registry.register(original)

        val replacement = ShimeTestService("economy")
        registry.register(replacement, replace = true)
        assertSame(replacement, registry.getByName("economy"))

        replacement.markReady()
        assertFailsWith<ShimeServiceException> {
            registry.register(ShimeTestService("economy"), replace = true)
        }
    }

    @Test
    fun `resolves dependencies before dependants and respects priority`() {
        val registry = registry()
        val independent = ShimeTestService("independent", servicePriority = 10)
        val dependent = ShimeTestService("dependent", serviceDependencies = setOf("independent"))
        val medium = ShimeTestService("medium", servicePriority = 5)
        registry.register(dependent)
        registry.register(medium)
        registry.register(independent)

        assertEquals(listOf("independent", "medium", "dependent"), registry.resolveOrder().map { it.serviceName })
    }

    @Test
    fun `reports a missing dependency`() {
        val registry = registry()
        registry.register(ShimeTestService("dependent", serviceDependencies = setOf("missing")))

        val exception = assertFailsWith<ShimeServiceException> { registry.resolveOrder() }
        assertEquals(
            "Service 'dependent' depends on 'missing', which is not registered.",
            exception.message
        )
    }

    @Test
    fun `reports circular dependencies`() {
        val registry = registry()
        registry.register(ShimeTestService("first", serviceDependencies = setOf("second")))
        registry.register(ShimeTestService("second", serviceDependencies = setOf("first")))

        val exception = assertFailsWith<ShimeServiceException> { registry.resolveOrder() }
        assertEquals(true, exception.message!!.contains("Circular service dependency detected"))
    }

    @Test
    fun `unregister removes a service`() {
        val registry = registry()
        registry.register(ShimeTestService("economy"))

        assertEquals(true, registry.unregister("economy"))
        assertEquals(false, registry.unregister("economy"))
        assertNull(registry.getByName("economy"))
    }

    @Test
    fun `requireByName explains which services exist`() {
        val registry = registry()
        registry.register(ShimeTestService("economy"))

        val exception = assertFailsWith<ShimeServiceException> { registry.requireByName("missing") }
        assertEquals(true, exception.message!!.contains("economy"))
    }
}
