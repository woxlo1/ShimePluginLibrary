package shimeserver.shimepluginlibrary.shimeconfig

import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ShimeConfig] の変更通知、購読解除、リスナー例外の分離を確認する。
 */
class ShimeConfigChangeTest {
    @TempDir
    lateinit var tempDir: Path

    private fun config(name: String = "change-test"): ShimeConfig {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        Mockito.`when`(plugin.dataFolder).thenReturn(tempDir.toFile())
        Mockito.`when`(plugin.getResource(Mockito.anyString())).thenReturn(null)
        return ShimeConfig(plugin, name, null, ShimeLogger(Logger.getLogger(name))).load()
    }

    @Test
    fun `setは変更を通知する`() {
        val config = config()
        val changes = CopyOnWriteArrayList<ShimeConfigChange>()
        config.addListener { changes += it }

        config.set("value", "one")

        assertEquals(1, changes.size)
        val change = changes.single()
        assertEquals("change-test", change.configName)
        assertEquals("value", change.path)
        assertEquals(null, change.oldValue)
        assertEquals("one", change.newValue)
        assertTrue(change.isAddition)
    }

    @Test
    fun `同じ値の設定は通知しない`() {
        val config = config()
        config.set("value", "one")
        val changes = CopyOnWriteArrayList<ShimeConfigChange>()
        config.addListener { changes += it }

        config.set("value", "one")

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `removeは削除を通知する`() {
        val config = config()
        config.set("value", "one")
        val changes = CopyOnWriteArrayList<ShimeConfigChange>()
        config.addListener { changes += it }

        config.remove("value")

        assertEquals(1, changes.size)
        val change = changes.single()
        assertEquals("one", change.oldValue)
        assertEquals(null, change.newValue)
        assertTrue(change.isRemoval)
        assertFalse(config.contains("value"))
    }

    @Test
    fun `reloadは差分だけを通知する`() {
        val config = config()
        config.set("kept", "same")
        config.set("changed", "before")
        config.set("removed", "gone")
        config.save()

        config.file.writeText(
            """
            kept: same
            changed: after
            added: new
            """.trimIndent() + "\n"
        )

        val changes = CopyOnWriteArrayList<ShimeConfigChange>()
        config.addListener { changes += it }
        config.reload()

        val byPath = changes.associateBy { it.path }
        assertEquals(setOf("changed", "added", "removed"), byPath.keys)
        assertEquals("before", byPath.getValue("changed").oldValue)
        assertEquals("after", byPath.getValue("changed").newValue)
        assertEquals("new", byPath.getValue("added").newValue)
        assertEquals("gone", byPath.getValue("removed").oldValue)
    }

    @Test
    fun `購読を解除すると通知されない`() {
        val config = config()
        val changes = CopyOnWriteArrayList<ShimeConfigChange>()
        val subscription = config.addListener { changes += it }
        assertEquals(1, config.listenerCount())

        subscription.cancel()
        config.set("value", "one")

        assertFalse(subscription.isActive)
        assertEquals(0, config.listenerCount())
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `リスナーの例外は他のリスナーと設定操作を妨げない`() {
        val config = config()
        val changes = CopyOnWriteArrayList<ShimeConfigChange>()
        config.addListener { throw IllegalStateException("listener failure") }
        config.addListener { changes += it }

        config.set("value", "one")

        assertEquals(1, changes.size)
        assertEquals("one", config.getString("value"))
    }
}
