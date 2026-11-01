package shimeserver.shimepluginlibrary.shimecommand

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [ShimeCommandNode] の名前正規化、階層、ヘルプ生成を検証する。
 */
class ShimeCommandNodeTest {
    @Test
    fun `名前は小文字に正規化される`() {
        val node = ShimeCommandNode("Admin")

        assertEquals("admin", node.name)
        assertEquals("admin", node.path)
        assertEquals("", node.relativePath)
    }

    @Test
    fun `不正な名前は拒否される`() {
        assertFailsWith<IllegalArgumentException> { ShimeCommandNode("bad name") }
        assertFailsWith<IllegalArgumentException> { ShimeCommandNode("") }
        assertFailsWith<IllegalArgumentException> { ShimeCommandNode("日本語") }
    }

    @Test
    fun `子ノードは名前とエイリアスの両方で解決できる`() {
        val root = shimeCommand("root") {
            subcommand("give") {
                alias("g")
            }
        }

        assertEquals("root give", root.child("give")?.path)
        assertEquals("root give", root.child("G")?.path)
        assertNull(root.child("missing"))
    }

    @Test
    fun `子ノードの重複とエイリアス衝突は拒否される`() {
        val root = ShimeCommandNode("root")
        root.addChild(ShimeCommandNode("first").also { it.aliases += "f" })

        assertFailsWith<IllegalArgumentException> { root.addChild(ShimeCommandNode("first")) }
        assertFailsWith<IllegalArgumentException> { root.addChild(ShimeCommandNode("second").also { it.aliases += "f" }) }
    }

    @Test
    fun `usageLine は必須引数と任意引数を区別する`() {
        val root = shimeCommand("shop") {
            subcommand("buy") {
                argument("item")
                argument("amount", ShimeArgumentTypes.integer(), optional = true)
            }
        }

        assertEquals("/shop buy <item> [amount]", root.child("buy")?.usageLine())
        assertEquals("/custom buy <item> [amount]", root.child("buy")?.usageLine("custom"))
    }

    @Test
    fun `usage を明示すると自動生成より優先される`() {
        val node = shimeCommand("shop") {
            usage("/shop help")
            argument("ignored")
        }

        assertEquals("/shop help", node.usageLine())
    }

    @Test
    fun `helpLines は説明と権限と子コマンドを含む`() {
        val root = shimeCommand("shop") {
            description("Shop commands")
            permission("shop.use")
            alias("s")
            subcommand("buy") {
                description("Buy an item")
            }
        }

        val lines = root.helpLines()
        assertTrue(lines.any { it.contains("Shop commands") })
        assertTrue(lines.any { it.contains("Permission: shop.use") })
        assertTrue(lines.any { it.contains("Aliases: s") })
        assertTrue(lines.any { it.contains("buy - Buy an item") })
    }

    @Test
    fun `children は追加順の読み取り専用ビューを返す`() {
        val root = shimeCommand("root") {
            subcommand("second") {}
            subcommand("first") {}
        }

        assertEquals(listOf("second", "first"), root.children.map { it.name })
        assertSame(root, root.children.first().parent)
    }
}
