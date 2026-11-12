package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import shimeserver.shimepluginlibrary.shimestorage.ShimeStorageCodec
import java.nio.file.Files
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * スコープのストレージが利用側プラグインのフォルダへ保存し、旧共有データを読み取れることを検証する。
 */
class ShimeStorageScopeTest {
    private val codec = object : ShimeStorageCodec<String> {
        override fun encode(value: String): Map<String, Any?> = mapOf("value" to value)

        override fun decode(data: Map<String, Any?>): String = data.getValue("value").toString()
    }

    private fun plugin(name: String): JavaPlugin {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val dataFolder = Files.createTempDirectory("shime-storage-$name").toFile()
        Mockito.`when`(plugin.name).thenReturn(name)
        Mockito.`when`(plugin.dataFolder).thenReturn(dataFolder)
        Mockito.`when`(plugin.logger).thenReturn(Logger.getLogger(name))
        return plugin
    }

    @Test
    fun `scoped storage writes under the consuming plugin folder`() {
        val libraryPlugin = plugin("shimepluginlibrary")
        val library = ShimeLibrary(libraryPlugin)
        val consumer = plugin("QuestPluginV2")
        val scope = library.forPlugin(consumer)
        val storage = scope.storageManager.createYaml("records", codec)

        storage.save("one", "value")

        assertTrue(Files.exists(consumer.dataFolder.toPath().resolve("storage/records/one.yml")))
        assertFalse(Files.exists(libraryPlugin.dataFolder.toPath().resolve("storage/records/one.yml")))
        assertEquals("value", storage.load("one"))
    }

    @Test
    fun `legacy shared data is readable but new writes use the consumer folder`() {
        val libraryPlugin = plugin("shimepluginlibrary")
        val library = ShimeLibrary(libraryPlugin)
        val legacy = library.storageManager.createYaml("records", codec)
        legacy.save("legacy", "old")
        val legacyFile = libraryPlugin.dataFolder.toPath().resolve("storage/records/legacy.yml")
        val consumer = plugin("QuestPluginV2")
        val scope = library.forPlugin(consumer)
        val storage = scope.storageManager.createYaml("records", codec)

        assertEquals("old", storage.load("legacy"))
        assertTrue(storage.exists("legacy"))
        assertEquals(setOf("legacy"), storage.keys())

        storage.save("legacy", "new")

        assertEquals("new", storage.load("legacy"))
        assertEquals("old", legacy.load("legacy"))
        assertTrue(Files.exists(consumer.dataFolder.toPath().resolve("storage/records/legacy.yml")))
        assertTrue(Files.exists(legacyFile))
    }

    @Test
    fun `legacy json data is readable through the scoped storage`() {
        val library = ShimeLibrary(plugin("shimepluginlibrary"))
        library.storageManager.createJson("records", String::class.java).save("legacy", "old")
        val scope = library.forPlugin(plugin("QuestPluginV2"))
        val storage = scope.storageManager.createJson("records", String::class.java)

        assertEquals("old", storage.load("legacy"))
        assertTrue(storage.keys().contains("legacy"))
    }

    @Test
    fun `closing a scope closes every storage it created`() {
        val library = ShimeLibrary(plugin("shimepluginlibrary"))
        val scope = library.forPlugin(plugin("QuestPluginV2"))
        scope.storageManager.createYaml("records", codec)

        scope.close()

        assertTrue(scope.storageManager.names().isEmpty())
    }
}
