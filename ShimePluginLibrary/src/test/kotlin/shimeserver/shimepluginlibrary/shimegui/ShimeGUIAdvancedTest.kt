package shimeserver.shimepluginlibrary.shimegui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShimeGUIAdvancedTest {
    @Test
    fun `owner and explicit access control are enforced`() {
        val gui = object : ShimeGUI("menu", Component.text("Menu"), 27) {
            override fun canAccess(player: Player): Boolean = false
        }
        val owner = player()
        val other = player()

        assertFalse(gui.hasAccess(owner))
        gui.setOwner(owner)
        assertEquals(owner.uniqueId, gui.getOwner())
        assertTrue(gui.hasAccess(owner))
        assertFalse(gui.hasAccess(other))

        gui.grantAccess(other)
        assertTrue(gui.hasAccess(other))
        gui.revokeAccess(other)
        assertFalse(gui.hasAccess(other))
    }

    @Test
    fun `custom access hook can grant access without ownership`() {
        val gui = object : ShimeGUI("open", Component.text("Open"), 9) {
            override fun canAccess(player: Player): Boolean = true
        }

        assertTrue(gui.hasAccess(player()))
    }

    @Test
    fun `list gui filters searches and sorts entries`() {
        val entries = listOf(
            ShimeGUIEntry("b", null, "Second", "2", "group-x"),
            ShimeGUIEntry("a", null, "First", "1", "group-y"),
            ShimeGUIEntry("c", null, "Third", "3", "group-x")
        )
        val gui = TestListGUI(entries)
        val player = player()

        assertEquals(listOf("a", "b", "c"), gui.visibleEntries(player).map { it.key })
        assertEquals(listOf("group-x", "group-y"), gui.groups(player))

        gui.setSearch(player, "thir")
        assertEquals(listOf("c"), gui.visibleEntries(player).map { it.key })
        assertEquals("thir", gui.searchQuery(player))

        gui.setSearch(player, "")
        gui.setGroupFilter(player, "group-x")
        assertEquals(listOf("b", "c"), gui.visibleEntries(player).map { it.key })
        assertEquals("group-x", gui.groupFilter(player))

        gui.setSortOrder(player, ShimeGUISortOrder.DESCENDING)
        assertEquals(listOf("c", "b"), gui.visibleEntries(player).map { it.key })

        gui.setFilter(player) { it.key != "c" }
        assertEquals(listOf("b"), gui.visibleEntries(player).map { it.key })

        gui.resetState(player)
        assertEquals("", gui.searchQuery(player))
        assertNull(gui.groupFilter(player))
        assertEquals(ShimeGUISortOrder.ASCENDING, gui.sortOrder(player))
    }

    @Test
    fun `list gui computes page count from filtered entries`() {
        val entries = (1..20).map { index ->
            ShimeGUIEntry("entry-$index", null, "Entry $index", index.toString().padStart(2, '0'))
        }
        val gui = TestListGUI(entries)
        val player = player()

        assertEquals(2, gui.pageCount(player))
        gui.setFilter(player) { it.key.endsWith("1") }
        assertEquals(1, gui.pageCount(player))
    }

    @Test
    fun `template context detects borders and validates size`() {
        val context = ShimeGUITemplateContext("template", 27)

        assertTrue(context.isBorder(0))
        assertTrue(context.isBorder(8))
        assertTrue(context.isBorder(18))
        assertTrue(context.isBorder(26))
        assertFalse(context.isBorder(10))
        assertFalse(context.isBorder(13))
    }

    @Test
    fun `template creates independent gui instances`() {
        var renders = 0
        val template = ShimeGUITemplate(
            id = "template",
            title = Component.text("Template"),
            size = 27,
            renderer = { _, _, _ -> renders += 1 }
        )

        val first = template.instantiate()
        val second = template.instantiate()

        assertEquals("template", first.id)
        assertEquals(27, first.size)
        assertFalse(first === second)
        first.setOwner(UUID.randomUUID())
        assertNull(second.getOwner())
    }

    @Test
    fun `dsl builder validates slots and builds a gui`() {
        val gui = shimeGUI("builder", Component.text("Builder"), 27) {
            border(null)
            slot(10, null)
        }

        assertEquals("builder", gui.id)
        assertEquals(27, gui.size)
        assertFailsWith<IllegalArgumentException> {
            ShimeGUIBuilder("bad", Component.text("Bad"), 9).slot(-1, null)
        }
        assertFailsWith<IllegalArgumentException> {
            ShimeGUIBuilder("bad", Component.text("Bad"), 9).onClick(9) { _, _ -> ShimeGUIAction.NONE }
        }
    }

    @Test
    fun `invalid gui definitions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            object : ShimeGUI(" ", Component.text("Bad"), 9) {}
        }
        assertFailsWith<IllegalArgumentException> {
            object : ShimeGUI("bad", Component.text("Bad"), 10) {}
        }
        assertFailsWith<IllegalArgumentException> {
            TestListGUI(emptyList(), size = 9)
        }
    }

    private fun player(): Player {
        val player = mock(Player::class.java)
        Mockito.`when`(player.uniqueId).thenReturn(UUID.randomUUID())
        return player
    }

    private class TestListGUI(
        private val values: List<ShimeGUIEntry>,
        size: Int = 27
    ) : ShimeListGUI("list", Component.text("List"), size) {
        override fun entries(player: Player): List<ShimeGUIEntry> = values
    }
}
