package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.lang.reflect.Field
import java.nio.file.Files
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * スコープのイベントマネージャーがリスナーを利用側プラグインに登録し、解放時に解除することを検証する。
 */
class ShimeEventScopeTest {
    private lateinit var pluginManager: PluginManager
    private lateinit var server: Server

    @BeforeEach
    fun installMockServer() {
        pluginManager = Mockito.mock(PluginManager::class.java)
        server = Mockito.mock(Server::class.java)
        Mockito.`when`(server.isPrimaryThread()).thenReturn(true)
        Mockito.`when`(server.pluginManager).thenReturn(pluginManager)
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

    private fun plugin(name: String): JavaPlugin {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val dataFolder = Files.createTempDirectory("shime-event-$name").toFile()
        Mockito.`when`(plugin.name).thenReturn(name)
        Mockito.`when`(plugin.dataFolder).thenReturn(dataFolder)
        Mockito.`when`(plugin.logger).thenReturn(Logger.getLogger(name))
        Mockito.`when`(plugin.server).thenReturn(server)
        return plugin
    }

    @Test
    fun `scoped listeners are registered with the consuming plugin`() {
        val library = ShimeLibrary(plugin("shimepluginlibrary"))
        val consumer = plugin("QuestPluginV2")
        val scope = library.forPlugin(consumer)
        val listener = object : Listener {}

        scope.eventManager.registerListener(listener)

        Mockito.verify(pluginManager).registerEvents(listener, consumer)
        assertTrue(scope.eventManager.isRegistered(listener))
        assertEquals(1, scope.eventManager.listenerCount())
    }

    @Test
    fun `scoped subscriptions are registered with the consuming plugin`() {
        val library = ShimeLibrary(plugin("shimepluginlibrary"))
        val consumer = plugin("QuestPluginV2")
        val scope = library.forPlugin(consumer)

        val subscription = scope.eventManager.subscribe(PlayerJoinEvent::class.java) {}

        assertTrue(subscription.isActive)
        assertEquals(1, scope.eventManager.subscriptions().size)
        Mockito.verify(pluginManager).registerEvent(
            Mockito.eq(PlayerJoinEvent::class.java),
            Mockito.any(Listener::class.java),
            Mockito.eq(EventPriority.NORMAL),
            Mockito.any(EventExecutor::class.java),
            Mockito.eq(consumer),
            Mockito.eq(false)
        )
    }

    @Test
    fun `closing a scope unregisters its listeners and subscriptions`() {
        val library = ShimeLibrary(plugin("shimepluginlibrary"))
        val consumer = plugin("QuestPluginV2")
        val scope = library.forPlugin(consumer)
        val listener = object : Listener {}
        scope.eventManager.registerListener(listener)
        val subscription = scope.eventManager.subscribe(PlayerJoinEvent::class.java) {}

        scope.close()

        assertFalse(subscription.isActive)
        assertTrue(scope.eventManager.subscriptions().isEmpty())
        assertEquals(0, scope.eventManager.listenerCount())
        assertFalse(scope.eventManager.isRegistered(listener))
    }
}
