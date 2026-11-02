package shimeserver.shimepluginlibrary.shimeconfig

import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ShimeConfigSchema] の型検証、依存関係チェック、既定値適用を確認する。
 */
class ShimeConfigSchemaTest {
    @TempDir
    lateinit var tempDir: Path

    private fun config(): ShimeConfig {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        Mockito.`when`(plugin.dataFolder).thenReturn(tempDir.toFile())
        Mockito.`when`(plugin.getResource(Mockito.anyString())).thenReturn(null)
        return ShimeConfig(plugin, "schema-test", null, ShimeLogger(Logger.getLogger("schema-test"))).load()
    }

    private fun schema(): ShimeConfigSchema {
        return ShimeConfigSchema(
            name = "schema-test",
            entries = listOf(
                ShimeConfigEntry.string("server-name", defaultValue = "Shime"),
                ShimeConfigEntry.integer(
                    "max-players",
                    defaultValue = 20,
                    validator = { if (it in 1..1_000) null else "max-players must be between 1 and 1000." }
                ),
                ShimeConfigEntry.boolean("features.economy", defaultValue = false),
                ShimeConfigEntry.string("database.host", required = true),
                ShimeConfigEntry.integer(
                    "database.port",
                    required = true,
                    dependsOn = setOf("database.host")
                )
            )
        )
    }

    @Test
    fun `既定値の適用は不足キーだけを書き込む`() {
        val config = config()
        config.set("server-name", "Custom")

        val applied = schema().applyDefaults(config)

        assertEquals(2, applied)
        assertEquals("Custom", config.getString("server-name"))
        assertEquals(20, config.getInt("max-players"))
        assertFalse(config.getBoolean("features.economy"))
        assertTrue(config.contains("database.host") == false)
    }

    @Test
    fun `必須値の欠落はエラーになる`() {
        val config = config()
        val report = schema().validate(config)

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.path == "database.host" })
        assertTrue(report.errors.any { it.path == "database.port" })
    }

    @Test
    fun `型変換できない値はエラーになる`() {
        val config = config()
        config.set("max-players", "not-a-number")
        config.set("database.host", "localhost")
        config.set("database.port", 3306)

        val report = schema().validate(config)

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.path == "max-players" })
    }

    @Test
    fun `追加のバリデータは範囲外の値を拒否する`() {
        val config = config()
        config.set("max-players", 0)
        config.set("database.host", "localhost")
        config.set("database.port", 3306)

        val report = schema().validate(config)

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.path == "max-players" && it.message.contains("between") })
    }

    @Test
    fun `依存関係の欠落はエラーになる`() {
        val config = config()
        config.set("database.port", 3306)

        val report = schema().validate(config)

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.path == "database.port" && it.message.contains("database.host") })
    }

    @Test
    fun `未知のキーは警告として報告される`() {
        val config = config()
        config.set("database.host", "localhost")
        config.set("database.port", 3306)
        config.set("unexpected", true)

        val report = schema().validate(config)

        assertTrue(report.isValid)
        assertTrue(report.warnings.any { it.path == "unexpected" })
    }

    @Test
    fun `正常な設定はクリーンと判定される`() {
        val config = config()
        config.set("database.host", "localhost")
        config.set("database.port", 3306)

        val report = schema().validate(config)

        assertTrue(report.isValid)
        assertTrue(report.isClean)
        assertEquals(0, report.errors.size)
        assertEquals(0, report.warnings.size)
    }
}
