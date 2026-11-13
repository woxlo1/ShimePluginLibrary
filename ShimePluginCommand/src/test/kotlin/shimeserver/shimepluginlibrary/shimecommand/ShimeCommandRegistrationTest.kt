package shimeserver.shimepluginlibrary.shimecommand

import org.bukkit.Server
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 動的コマンドがサーバーのコマンドマップへ登録されることを検証する。
 */
class ShimeCommandRegistrationTest {
    private class Fixture(
        val plugin: JavaPlugin,
        val commandMap: CommandMap,
        val manager: ShimeCommandManager
    )

    private fun fixture(name: String, accepted: Boolean = true): Fixture {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val commandMap = Mockito.mock(CommandMap::class.java)
        Mockito.`when`(plugin.name).thenReturn(name)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.commandMap).thenReturn(commandMap)
        Mockito.`when`(
            commandMap.register(Mockito.anyString(), Mockito.any(Command::class.java))
        ).thenReturn(accepted)
        val logger = ShimeLogger(Logger.getLogger(name))
        val manager = ShimeCommandManager(plugin, logger, ShimeScheduler(plugin, logger))
        return Fixture(plugin, commandMap, manager)
    }

    @Test
    fun `dynamic commands are registered with the plugin name as fallback prefix`() {
        val fixture = fixture("QuestPluginV2")

        fixture.manager.register(ShimeCommandNode("quest"), dynamic = true)

        val captor = org.mockito.ArgumentCaptor.forClass(Command::class.java)
        Mockito.verify(fixture.commandMap).register(Mockito.eq("questpluginv2"), captor.capture())
        assertEquals("quest", captor.value.name)
    }

    @Test
    fun `rejected dynamic registration throws`() {
        val fixture = fixture("QuestPluginV2", accepted = false)

        assertFailsWith<ShimeCommandException> {
            fixture.manager.register(ShimeCommandNode("quest"), dynamic = true)
        }
    }
}
