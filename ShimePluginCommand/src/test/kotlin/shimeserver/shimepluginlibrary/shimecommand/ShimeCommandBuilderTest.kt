package shimeserver.shimepluginlibrary.shimecommand

import org.junit.jupiter.api.Test
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * [ShimeCommandBuilder] の検証と Kotlin/Java 両方の DSL を確認する。
 */
class ShimeCommandBuilderTest {
    @Test
    fun `エイリアスは正規化され重複しない`() {
        val node = shimeCommand("root") {
            alias("R", "r", "x")
        }

        assertEquals(setOf("r", "x"), node.aliases)
    }

    @Test
    fun `不正なエイリアスは拒否される`() {
        assertFailsWith<IllegalArgumentException> {
            shimeCommand("root") { alias("bad alias") }
        }
        assertFailsWith<IllegalArgumentException> {
            shimeCommand("root") { alias("root") }
        }
    }

    @Test
    fun `権限の空文字は拒否される`() {
        assertFailsWith<IllegalArgumentException> {
            shimeCommand("root") { permission(" ") }
        }
    }

    @Test
    fun `クールダウンと実行制限は負値を拒否する`() {
        assertFailsWith<IllegalArgumentException> {
            shimeCommand("root") { cooldown(-1) }
        }
        assertFailsWith<IllegalArgumentException> {
            shimeCommand("root") { limit(0, 1000) }
        }
        assertFailsWith<IllegalArgumentException> {
            shimeCommand("root") { limit(1, 0) }
        }
    }

    @Test
    fun `ネストしたサブコマンドは完全なパスを持つ`() {
        val root = shimeCommand("admin") {
            subcommand("reload") {
                subcommand("config") {}
            }
        }

        val nested = root.child("reload")?.child("config")
        assertEquals("admin reload config", nested?.path)
        assertEquals("reload config", nested?.relativePath)
    }

    @Test
    fun `実行制限と送信者種別を設定できる`() {
        val node = shimeCommand("root") {
            playerOnly()
            cooldown(500)
            limit(3, 10_000)
            description("description")
        }

        assertEquals(ShimeCommandSenderType.PLAYER, node.senderType)
        assertEquals(500L, node.cooldownMillis)
        assertEquals(3, node.executionLimit)
        assertEquals(10_000L, node.executionWindowMillis)
        assertEquals("description", node.description)
    }

    @Test
    fun `build は構築済みの同一ノードを返す`() {
        val builder = ShimeCommandDsl.builder("root")
        assertSame(builder.node, builder.build())
    }

    @Test
    fun `Java 向け DSL でもコマンドを構築できる`() {
        val node = ShimeCommandDsl.command("root", Consumer { builder ->
            builder.description("java")
            builder.subcommand("child") { description("child") }
        })

        assertEquals("java", node.description)
        assertEquals("root child", node.child("child")?.path)
    }

    @Test
    fun `任意引数は既定値を持つ`() {
        val node = shimeCommand("root") {
            argument("amount", ShimeArgumentTypes.integer(), optional = true, defaultValue = 1)
        }

        assertEquals("amount", node.arguments.single().name)
        assertEquals(true, node.arguments.single().optional)
        assertEquals(1, node.arguments.single().defaultValue)
    }
}
