package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * スコープのメッセージマネージャーが利用側の言語ファイルを優先し、共有ファイルで補完することを検証する。
 */
class ShimeMessageScopeTest {
    private fun plugin(name: String): JavaPlugin {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val dataFolder = Files.createTempDirectory("shime-message-$name").toFile()
        Mockito.`when`(plugin.name).thenReturn(name)
        Mockito.`when`(plugin.dataFolder).thenReturn(dataFolder)
        Mockito.`when`(plugin.logger).thenReturn(Logger.getLogger(name))
        return plugin
    }

    private fun writeMessages(plugin: JavaPlugin, fileName: String, content: String) {
        val directory = File(plugin.dataFolder, "messages")
        assertTrue(directory.exists() || directory.mkdirs())
        File(directory, fileName).writeText(content, StandardCharsets.UTF_8)
    }

    @Test
    fun `consumer messages override the shared messages`() {
        val libraryPlugin = plugin("shimepluginlibrary")
        writeMessages(libraryPlugin, "en_US.yml", "both: library\nshared: library-only\n")
        val consumer = plugin("QuestPluginV2")
        writeMessages(consumer, "en_US.yml", "both: consumer\n")
        val library = ShimeLibrary(libraryPlugin)

        val scope = library.forPlugin(consumer)

        assertEquals("consumer", scope.messageManager.raw("en_US", "both"))
        assertEquals("library-only", scope.messageManager.raw("en_US", "shared"))
        assertEquals(setOf("en_US"), scope.messageManager.locales())
    }

    @Test
    fun `locales missing from the consumer are read from the shared messages`() {
        val libraryPlugin = plugin("shimepluginlibrary")
        writeMessages(libraryPlugin, "en_US.yml", "greeting: hello\n")
        writeMessages(libraryPlugin, "ja_JP.yml", "greeting: こんにちは\n")
        val consumer = plugin("QuestPluginV2")
        writeMessages(consumer, "fr_FR.yml", "greeting: bonjour\n")
        val library = ShimeLibrary(libraryPlugin)

        val scope = library.forPlugin(consumer)

        assertEquals("bonjour", scope.messageManager.raw("fr_FR", "greeting"))
        assertEquals("こんにちは", scope.messageManager.raw("ja_JP", "greeting"))
        assertEquals(setOf("en_US", "fr_FR", "ja_JP"), scope.messageManager.locales())
    }

    @Test
    fun `default language files are extracted from the consumer jar`() {
        val libraryPlugin = plugin("shimepluginlibrary")
        val consumer = plugin("QuestPluginV2")
        val content = "greeting: hi\n"
        Mockito.`when`(consumer.getResource("messages/en_US.yml")).thenAnswer {
            ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
        }
        Mockito.doAnswer {
            File(consumer.dataFolder, "messages/en_US.yml").writeText(content, StandardCharsets.UTF_8)
        }.`when`(consumer).saveResource("messages/en_US.yml", false)
        val library = ShimeLibrary(libraryPlugin)

        val scope = library.forPlugin(consumer)

        assertEquals("hi", scope.messageManager.raw("en_US", "greeting"))
        Mockito.verify(consumer).saveResource("messages/en_US.yml", false)
        Mockito.verify(libraryPlugin, Mockito.never()).saveResource(Mockito.anyString(), Mockito.anyBoolean())
    }
}
