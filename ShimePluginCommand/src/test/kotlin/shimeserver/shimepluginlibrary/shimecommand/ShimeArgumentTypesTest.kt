package shimeserver.shimepluginlibrary.shimecommand

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 標準引数パーサーの検証、境界値、候補生成を確認する。
 */
class ShimeArgumentTypesTest {
    private val context: ShimeCommandContext = mock(ShimeCommandContext::class.java)

    @Test
    fun `文字列は空文字を拒否する`() {
        val argument = ShimeArgumentTypes.string()

        assertEquals("value", argument.parse("value", context))
        assertFailsWith<ShimeCommandArgumentException> { argument.parse(" ", context) }
    }

    @Test
    fun `整数は範囲を検証する`() {
        val argument = ShimeArgumentTypes.integer(min = 1, max = 10)

        assertEquals(1, argument.parse("1", context))
        assertEquals(10, argument.parse("10", context))
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("0", context) }
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("11", context) }
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("abc", context) }
    }

    @Test
    fun `長整数は範囲を検証する`() {
        val argument = ShimeArgumentTypes.long(min = 0L, max = 100L)

        assertEquals(100L, argument.parse("100", context))
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("-1", context) }
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("1.5", context) }
    }

    @Test
    fun `小数は有限値のみ受け付ける`() {
        val argument = ShimeArgumentTypes.decimal(min = 0.0, max = 1.0)

        assertEquals(0.5, argument.parse("0.5", context))
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("NaN", context) }
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("Infinity", context) }
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("-0.1", context) }
    }

    @Test
    fun `真偽値は複数の表記を受け付ける`() {
        val argument = ShimeArgumentTypes.boolean()

        assertTrue(argument.parse("true", context))
        assertTrue(argument.parse("YES", context))
        assertTrue(argument.parse("on", context))
        assertEquals(false, argument.parse("false", context))
        assertEquals(false, argument.parse("no", context))
        assertEquals(false, argument.parse("OFF", context))
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("maybe", context) }
    }

    @Test
    fun `UUID は標準形式のみ受け付ける`() {
        val argument = ShimeArgumentTypes.uuid()
        val value = UUID.randomUUID()

        assertEquals(value, argument.parse(value.toString(), context))
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("not-a-uuid", context) }
    }

    @Test
    fun `選択肢は大文字小文字を無視して正規の表記を返す`() {
        val argument = ShimeArgumentTypes.choice(listOf("Survival", "Creative"))

        assertEquals("Survival", argument.parse("survival", context))
        assertEquals("Creative", argument.parse("CREATIVE", context))
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("adventure", context) }
        assertFailsWith<IllegalArgumentException> { ShimeArgumentTypes.choice(emptyList()) }
    }

    @Test
    fun `列挙値は大文字小文字を無視して解決する`() {
        val argument = ShimeArgumentTypes.enumeration(TestMode::class.java)

        assertEquals(TestMode.FIRST, argument.parse("first", context))
        assertEquals(TestMode.SECOND, argument.parse("SECOND", context))
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("third", context) }
    }

    @Test
    fun `カスタムパーサーは例外を引数エラーへ変換する`() {
        val argument = ShimeArgumentTypes.custom("custom", parser = { input, _ ->
            if (input == "bad") {
                error("boom")
            }
            input
        })

        assertEquals("ok", argument.parse("ok", context))
        assertFailsWith<ShimeCommandArgumentException> { argument.parse("bad", context) }
    }

    @Test
    fun `カスタムパーサーは引数エラーをそのまま伝播する`() {
        val expected = ShimeCommandArgumentException("expected")
        val argument = ShimeArgumentTypes.custom<String>("custom", parser = { _, _ -> throw expected })

        val actual = assertFailsWith<ShimeCommandArgumentException> { argument.parse("value", context) }
        assertEquals(expected, actual)
    }

    @Test
    fun `候補生成は部分入力で絞り込まれる`() {
        val boolean = ShimeArgumentTypes.boolean()
        val choice = ShimeArgumentTypes.choice(listOf("alpha", "beta"))
        val enumeration = ShimeArgumentTypes.enumeration(TestMode::class.java)

        assertEquals(listOf("true"), boolean.complete(context, "tr"))
        assertEquals(listOf("beta"), choice.complete(context, "be"))
        assertEquals(listOf("SECOND"), enumeration.complete(context, "se"))
        assertEquals(emptyList(), choice.complete(context, "zzz"))
    }

    private enum class TestMode {
        FIRST,
        SECOND
    }
}
