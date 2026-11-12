package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import java.lang.reflect.Field
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [ShimePluginScope] のキャッシュ同一性とライフサイクルを検証する。
 *
 * 実際の Bukkit サーバーは起動せず、[JavaPlugin] をモックしてスコープの管理だけを対象にする。
 */
class ShimePluginScopeTest {
    @BeforeEach
    fun installMockServer() {
        val server = Mockito.mock(Server::class.java)
        Mockito.`when`(server.isPrimaryThread()).thenReturn(true)
        setBukkitServer(server)
    }

    @AfterEach
    fun removeMockServer() {
        setBukkitServer(null)
    }

    private fun setBukkitServer(server: Server?) {
        val field: Field = Bukkit::class.java.getDeclaredField("server")
        field.isAccessible = true
        field.set(null, server)
    }

    private fun plugin(name: String, loggerName: String = name): JavaPlugin {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val dataFolder = Files.createTempDirectory("shime-scope-$name").toFile()
        Mockito.`when`(plugin.name).thenReturn(name)
        Mockito.`when`(plugin.logger).thenReturn(Logger.getLogger(loggerName))
        Mockito.`when`(plugin.dataFolder).thenReturn(dataFolder)
        return plugin
    }

    private fun library(name: String = "shimepluginlibrary"): ShimeLibrary {
        return ShimeLibrary(plugin(name))
    }

    @Test
    fun `forPlugin returns the same scope for the same plugin`() {
        val library = library()
        val consumer = plugin("QuestPluginV2")

        val first = library.forPlugin(consumer)
        val second = library.forPlugin(consumer)

        assertSame(first, second)
        assertEquals(listOf(first), library.scopes())
    }

    @Test
    fun `different plugins receive different scopes`() {
        val library = library()
        val first = library.forPlugin(plugin("FirstPlugin"))
        val second = library.forPlugin(plugin("SecondPlugin"))

        assertNotSame(first, second)
        assertEquals(2, library.scopes().size)
    }

    @Test
    fun `closing a scope removes it from the cache and reacquiring creates a new scope`() {
        val library = library()
        val consumer = plugin("QuestPluginV2")

        val first = library.forPlugin(consumer)
        first.close()

        assertTrue(first.isClosed)
        assertTrue(library.scopes().isEmpty())
        // close is idempotent.
        first.close()

        val second = library.forPlugin(consumer)
        assertNotSame(first, second)
        assertFalse(second.isClosed)
    }

    @Test
    fun `disabling a consumer plugin releases only its scope`() {
        val library = library()
        val consumer = plugin("QuestPluginV2")
        val other = plugin("OtherPlugin")

        val consumerScope = library.forPlugin(consumer)
        val otherScope = library.forPlugin(other)
        val listener = ShimeScopeDisableListener(library)

        listener.onPluginDisable(PluginDisableEvent(consumer))

        assertTrue(consumerScope.isClosed)
        assertFalse(otherScope.isClosed)
        assertEquals(listOf(otherScope), library.scopes())
    }

    @Test
    fun `scope logger records messages under the consuming plugin name`() {
        val library = library()
        val records = java.util.Collections.synchronizedList(ArrayList<java.util.logging.LogRecord>())
        val consumerLogger = Logger.getLogger("QuestPluginV2")
        consumerLogger.useParentHandlers = false
        val handler = object : java.util.logging.Handler() {
            override fun publish(record: java.util.logging.LogRecord) {
                records += record
            }

            override fun flush() = Unit

            override fun close() = Unit
        }
        consumerLogger.addHandler(handler)

        try {
            val scope = library.forPlugin(plugin("QuestPluginV2", consumerLogger.name))
            scope.logger.info(ShimeLogCategory.CORE, "hello from the consumer")

            assertEquals(1, records.size)
            assertEquals("QuestPluginV2", records.first().loggerName)
            assertEquals("[core] hello from the consumer", records.first().message)
            assertNotSame(library.logger, scope.logger)
        } finally {
            consumerLogger.removeHandler(handler)
        }
    }

    @Test
    fun `shutdown releases every scope`() {
        val library = library()
        val first = library.forPlugin(plugin("FirstPlugin"))
        val second = library.forPlugin(plugin("SecondPlugin"))

        library.shutdown()

        assertTrue(first.isClosed)
        assertTrue(second.isClosed)
        assertTrue(library.scopes().isEmpty())
    }

    @Test
    fun `concurrent forPlugin calls return a single scope`() {
        val library = library()
        val consumer = plugin("QuestPluginV2")
        val pool = Executors.newFixedThreadPool(8)

        try {
            val tasks = (1..64).map {
                Callable { library.forPlugin(consumer) }
            }
            val results = pool.invokeAll(tasks).map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1, results.distinct().size)
            assertSame(results.first(), library.forPlugin(consumer))
        } finally {
            pool.shutdownNow()
        }
    }
}
