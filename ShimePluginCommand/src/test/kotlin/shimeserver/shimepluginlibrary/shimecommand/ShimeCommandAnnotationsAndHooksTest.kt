package shimeserver.shimepluginlibrary.shimecommand

import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * アノテーション方式のコマンド定義とフック・監査・統計を検証する。
 */
class ShimeCommandAnnotationsAndHooksTest {
    private val context: ShimeCommandContext = mock(ShimeCommandContext::class.java)

    @Test
    fun `アノテーションからコマンドツリーを構築できる`() {
        val target = AnnotationSample()
        val root = ShimeCommandAnnotations.build(target)

        assertEquals("annotest", root.name)
        assertEquals("annotest run", root.child("run")?.path)
        assertEquals("annotest run fast", root.child("run")?.child("fast")?.path)
        assertNotNull(root.child("run")?.executor)
    }

    @Test
    fun `アノテーションの引数と権限を解決できる`() {
        val root = ShimeCommandAnnotations.build(AnnotationArguments())
        val node = root.child("check")
        assertNotNull(node)
        assertEquals("check.use", node!!.permission)
        assertEquals(1, node.arguments.size)
        assertEquals("count", node.arguments[0].name)
        assertEquals(true, node.arguments[0].optional)
    }

    @Test
    fun `クラスに定義アノテーションがない場合は失敗する`() {
        assertFailsWith<ShimeCommandException> { ShimeCommandAnnotations.build(BadNoDefinition()) }
    }

    @Test
    fun `ハンドラーが存在しない場合は失敗する`() {
        assertFailsWith<ShimeCommandException> { ShimeCommandAnnotations.build(BadNoHandler()) }
    }

    @Test
    fun `インスタンスが必要なハンドラーで null を渡すと失敗する`() {
        assertFailsWith<ShimeCommandException> { ShimeCommandAnnotations.build(BadInstanceRequired::class.java, null) }
    }

    @Test
    fun `重複したパスは拒否される`() {
        assertFailsWith<ShimeCommandException> { ShimeCommandAnnotations.build(BadDuplicate()) }
    }

    @Test
    fun `フックは登録順に before 実行され逆順に after 実行される`() {
        val log = mutableListOf<String>()
        val manager = manager()
        manager.addHook(TestHook("global", log, allowBefore = true))
        val root = shimeCommand("root") {
            hook(TestHook("node", log, allowBefore = true))
            executes { _, _ ->
                log += "exec"
                ShimeCommandResult.SilentSuccess
            }
        }

        manager.dispatch(root, sender(), "root", emptyList())

        assertEquals(listOf("before:global", "before:node", "exec", "after:node", "after:global"), log)
        assertEquals(1, manager.statistics("root")?.executions)
        assertEquals(1, manager.auditLog.size())
    }

    @Test
    fun `フックが false を返すと実行を中止し Cancelled を返す`() {
        val log = mutableListOf<String>()
        val manager = manager()
        val root = shimeCommand("root") {
            hook(object : ShimeCommandHook {
                override fun beforeExecution(context: ShimeCommandContext): Boolean {
                    log += "before"
                    return false
                }

                override fun afterExecution(context: ShimeCommandContext, result: ShimeCommandResult) {
                    log += "after"
                }
            })
            executes { _, _ ->
                log += "exec"
                ShimeCommandResult.Success("ok")
            }
        }

        manager.dispatch(root, sender(), "root", emptyList())

        assertEquals(listOf("before"), log)
        assertNull(manager.statistics("root"))
        assertEquals("Cancelled", manager.auditLog.recent(1).single().resultType)
        assertFalse(manager.auditLog.recent(1).single().success)
    }

    @Test
    fun `フックの例外は後続のフックと実行に影響しない`() {
        val log = mutableListOf<String>()
        val manager = manager()
        val root = shimeCommand("root") {
            hook(object : ShimeCommandHook {
                override fun beforeExecution(context: ShimeCommandContext): Boolean {
                    error("boom")
                }
            })
            hook(TestHook("second", log, allowBefore = true))
            executes { _, _ ->
                log += "exec"
                ShimeCommandResult.SilentSuccess
            }
        }

        manager.dispatch(root, sender(), "root", emptyList())

        assertEquals(listOf("before:second", "exec", "after:second"), log)
    }

    @Test
    fun `引数エラーは位置と引数名を表示する`() {
        val manager = manager()
        val sender = sender()
        val root = shimeCommand("root") {
            argument("amount", ShimeArgumentTypes.integer())
            executes { _, _ -> ShimeCommandResult.SilentSuccess }
        }

        manager.dispatch(root, sender, "root", listOf("abc"))

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(sender, atLeastOnce()).sendMessage(captor.capture())
        val messages = captor.allValues.joinToString("\n")
        assertTrue(messages.contains("#1"))
        assertTrue(messages.contains("amount"))
        assertTrue(messages.contains("abc"))
    }

    private fun manager(): ShimeCommandManager {
        return ShimeCommandManager(
            mock(org.bukkit.plugin.java.JavaPlugin::class.java),
            mock(shimeserver.shimepluginlibrary.shimelogging.ShimeLogger::class.java),
            mock(shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler::class.java)
        )
    }

    private fun sender(): org.bukkit.command.CommandSender {
        val sender = mock(org.bukkit.command.CommandSender::class.java)
        `when`(sender.name).thenReturn("tester")
        `when`(sender.hasPermission(anyString())).thenReturn(true)
        return sender
    }

    @Test
    fun `カスタム引数型を登録してアノテーションで使える`() {
        ShimeCommandAnnotations.registerArgumentType("code", ShimeArgumentTypes.choice(listOf("a", "b")))
        val root = ShimeCommandAnnotations.build(CustomArgSample())
        assertNotNull(root.child("apply")?.arguments?.firstOrNull()?.argument)
    }

    @Test
    fun `実行統計のスナップショットは空の集計を返す`() {
        val manager = ShimeCommandManager(mock(org.bukkit.plugin.java.JavaPlugin::class.java), mock(shimeserver.shimepluginlibrary.shimelogging.ShimeLogger::class.java), mock(shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler::class.java))
        assertTrue(manager.statistics().isEmpty())
        assertNull(manager.statistics("missing"))
    }

    @ShimeCommandDefinition(name = "annotest", description = "annotation test")
    class AnnotationSample {
        @ShimeCommandHandler
        fun root(context: ShimeCommandContext): ShimeCommandResult {
            return ShimeCommandResult.SilentSuccess
        }

        @ShimeCommandHandler(path = ["run"])
        fun run(): String = "run"

        @ShimeCommandHandler(path = ["run", "fast"])
        fun fast(): Boolean = true
    }

    @ShimeCommandDefinition(name = "argtest")
    class AnnotationArguments {
        @ShimeCommandHandler(path = ["check"], permission = "check.use")
        fun check(@ShimeCommandArg(name = "count", optional = true, hasDefault = true, defaultValue = "1") count: Int): ShimeCommandResult {
            return ShimeCommandResult.SilentSuccess
        }
    }

    class BadNoDefinition {
        @ShimeCommandHandler
        fun run() {}
    }

    @ShimeCommandDefinition(name = "bad")
    class BadNoHandler {
        fun notAHandler() {}
    }

    @ShimeCommandDefinition(name = "bad")
    class BadInstanceRequired {
        @ShimeCommandHandler
        fun run() {}
    }

    @ShimeCommandDefinition(name = "bad")
    class BadDuplicate {
        @ShimeCommandHandler(path = ["x"])
        fun first(): ShimeCommandResult = ShimeCommandResult.SilentSuccess

        @ShimeCommandHandler(path = ["x"])
        fun second(): ShimeCommandResult = ShimeCommandResult.SilentSuccess
    }

    @ShimeCommandDefinition(name = "custom")
    class CustomArgSample {
        @ShimeCommandHandler(path = ["apply"])
        fun apply(@ShimeCommandArg(type = ShimeCommandArgumentType.CUSTOM, customType = "code") code: String): ShimeCommandResult {
            return ShimeCommandResult.SilentSuccess
        }
    }

    class TestHook(
        private val name: String,
        private val log: MutableList<String>,
        private val allowBefore: Boolean
    ) : ShimeCommandHook {
        override fun beforeExecution(context: ShimeCommandContext): Boolean {
            log += "before:$name"
            return allowBefore
        }

        override fun afterExecution(context: ShimeCommandContext, result: ShimeCommandResult) {
            log += "after:$name"
        }
    }
}
