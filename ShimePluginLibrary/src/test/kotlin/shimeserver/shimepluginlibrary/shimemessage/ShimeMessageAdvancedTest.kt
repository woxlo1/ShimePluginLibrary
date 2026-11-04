package shimeserver.shimepluginlibrary.shimemessage

import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShimeMessageAdvancedTest {
    private fun manager(): ShimeMessageManager {
        val manager = ShimeMessageManager(
            mock(JavaPlugin::class.java),
            ShimeLogger(Logger.getLogger("ShimeMessageAdvancedTest")),
            mock(ShimeScheduler::class.java)
        )
        manager.configure("en_US", false)
        return manager
    }

    @Test
    fun `template extracts placeholders from both brace styles`() {
        val template = ShimeMessageTemplate(
            key = "greeting",
            defaultText = "Hello {name}, welcome to %world%."
        )

        assertEquals(setOf("name", "world"), template.placeholders())
    }

    @Test
    fun `explicit required placeholders override extraction`() {
        val template = ShimeMessageTemplate(
            key = "greeting",
            defaultText = "Hello {name}",
            requiredPlaceholders = setOf("player")
        )

        assertEquals(setOf("player"), template.placeholders())
    }

    @Test
    fun `registered templates are resolvable and validate placeholders`() {
        val manager = manager()
        manager.registerTemplate(ShimeMessageTemplate("greeting", "<green>Hello {name}</green>"))

        assertNotNull(manager.template("en_US", "greeting"))
        assertEquals("<green>Hello {name}</green>", manager.raw("en_US", "greeting"))
        assertTrue(manager.hasMessage("en_US", "greeting"))

        val missing = manager.validate("en_US", "greeting")
        assertFalse(missing.isValid)
        assertEquals(setOf("name"), missing.missingPlaceholders)
        assertTrue(missing.summary().contains("name"))

        val valid = manager.validate("en_US", "greeting", supplied = setOf("name"))
        assertTrue(valid.isValid, valid.summary())
    }

    @Test
    fun `validation reports a missing key`() {
        val manager = manager()

        val report = manager.validate("en_US", "absent")

        assertFalse(report.isValid)
        assertTrue(report.issues.any { it.type == ShimeMessageIssueType.MISSING_KEY })
    }

    @Test
    fun `additional required placeholders are merged into validation`() {
        val manager = manager()
        manager.registerTemplate(ShimeMessageTemplate("notice", "Notice"))

        val report = manager.validate("en_US", "notice", supplied = emptySet(), required = setOf("target"))

        assertFalse(report.isValid)
        assertEquals(setOf("target"), report.missingPlaceholders)
    }

    @Test
    fun `component cache is bounded and can be cleared`() {
        val manager = manager()
        manager.registerTemplate(ShimeMessageTemplate("greeting", "<green>Hello {name}</green>"))

        manager.component("en_US", "greeting", mapOf("name" to "Shime"))
        manager.component("en_US", "greeting", mapOf("name" to "Shime"))
        assertEquals(1, manager.cachedMessages())

        manager.clearCache()
        assertEquals(0, manager.cachedMessages())
    }

    @Test
    fun `unregistering templates removes them from lookups`() {
        val manager = manager()
        manager.registerTemplate(ShimeMessageTemplate("greeting", "Hello"))

        assertTrue(manager.unregisterTemplate("greeting"))
        assertFalse(manager.unregisterTemplate("greeting"))
        assertNull(manager.template("en_US", "greeting"))

        manager.registerTemplate("ja_JP", ShimeMessageTemplate("greeting", "こんにちは"))
        assertNotNull(manager.template("ja_JP", "greeting"))
        manager.clearTemplates()
        assertNull(manager.template("ja_JP", "greeting"))
    }
}
