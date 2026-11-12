package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * スコープの設定マネージャーが利用側プラグインのデータフォルダと JAR を使うことを検証する。
 */
class ShimeConfigScopeTest {
    private fun plugin(name: String): JavaPlugin {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val dataFolder = Files.createTempDirectory("shime-config-$name").toFile()
        Mockito.`when`(plugin.name).thenReturn(name)
        Mockito.`when`(plugin.dataFolder).thenReturn(dataFolder)
        Mockito.`when`(plugin.logger).thenReturn(Logger.getLogger(name))
        return plugin
    }

    @Test
    fun `scoped configurations are stored in the consuming plugin data folder`() {
        val libraryPlugin = plugin("shimepluginlibrary")
        val library = ShimeLibrary(libraryPlugin)
        val consumer = plugin("QuestPluginV2")
        val scope = library.forPlugin(consumer)

        val config = scope.configManager.create("settings", "settings.yml")

        assertEquals(File(consumer.dataFolder, "settings.yml"), config.file)
        assertTrue(config.file.exists())
        assertFalse(File(libraryPlugin.dataFolder, "settings.yml").exists())
    }

    @Test
    fun `embedded defaults are read from the consuming plugin jar`() {
        val libraryPlugin = plugin("shimepluginlibrary")
        val library = ShimeLibrary(libraryPlugin)
        val consumer = plugin("QuestPluginV2")
        val scope = library.forPlugin(consumer)
        val resource = "greeting: hello\ncount: 3\n"
        Mockito.`when`(consumer.getResource("settings.yml")).thenAnswer {
            ByteArrayInputStream(resource.toByteArray(StandardCharsets.UTF_8))
        }
        Mockito.doAnswer {
            File(consumer.dataFolder, "settings.yml").writeText(resource, StandardCharsets.UTF_8)
        }.`when`(consumer).saveResource("settings.yml", false)

        val config = scope.configManager.create("settings")

        assertEquals("hello", config.getString("greeting"))
        assertEquals(3, config.getInt("count"))
        Mockito.verify(consumer).saveResource("settings.yml", false)
        Mockito.verify(libraryPlugin, Mockito.never()).saveResource(Mockito.anyString(), Mockito.anyBoolean())
    }

    @Test
    fun `closing a scope saves the configurations it owns`() {
        val library = ShimeLibrary(plugin("shimepluginlibrary"))
        val consumer = plugin("QuestPluginV2")
        val scope = library.forPlugin(consumer)
        val config = scope.configManager.create("settings", "settings.yml")
        config.set("value", "kept")

        scope.close()

        val saved = YamlConfiguration.loadConfiguration(File(consumer.dataFolder, "settings.yml"))
        assertEquals("kept", saved.getString("value"))
    }

    @Test
    fun `the library config manager keeps the legacy data folder`() {
        val libraryPlugin = plugin("shimepluginlibrary")
        val library = ShimeLibrary(libraryPlugin)

        val config = library.configManager.create("legacy", "legacy.yml")

        assertEquals(File(libraryPlugin.dataFolder, "legacy.yml"), config.file)
        assertTrue(config.file.exists())
    }
}
