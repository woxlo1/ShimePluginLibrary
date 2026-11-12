package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import shimeserver.shimepluginlibrary.shimeentity.ShimeEntityService
import shimeserver.shimepluginlibrary.shimeitem.ShimeItemIdentifier
import java.util.UUID
import kotlin.test.assertEquals

/**
 * スコープの識別子が利用側プラグインの名前空間へ書き込み、旧ライブラリの名前空間を読み取れることを検証する。
 */
class ShimeIdentifierScopeTest {
    private fun plugin(name: String): JavaPlugin {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        Mockito.`when`(plugin.name).thenReturn(name)
        return plugin
    }

    private fun key(namespace: String, path: String): NamespacedKey =
        NamespacedKey.fromString("${namespace.lowercase()}:$path")!!

    private class ItemMock(val item: ItemStack, val container: PersistentDataContainer)

    private fun item(): ItemMock {
        val item = Mockito.mock(ItemStack::class.java)
        val meta = Mockito.mock(ItemMeta::class.java)
        val container = Mockito.mock(PersistentDataContainer::class.java)
        Mockito.`when`(item.itemMeta).thenReturn(meta)
        Mockito.`when`(meta.persistentDataContainer).thenReturn(container)
        return ItemMock(item, container)
    }

    private class EntityMock(val entity: Entity, val container: PersistentDataContainer)

    private fun entity(): EntityMock {
        val entity = Mockito.mock(Entity::class.java)
        val container = Mockito.mock(PersistentDataContainer::class.java)
        Mockito.`when`(entity.persistentDataContainer).thenReturn(container)
        return EntityMock(entity, container)
    }

    @Test
    fun `item tags are written under the consumer namespace`() {
        val identifier = ShimeItemIdentifier(plugin("QuestPluginV2"), plugin("shimepluginlibrary"))
        val mock = item()

        identifier.tag(mock.item, "sword", 2)

        assertEquals("questpluginv2", identifier.key().namespace)
        Mockito.verify(mock.container).set(key("QuestPluginV2", "item_id"), PersistentDataType.STRING, "sword")
        Mockito.verify(mock.container).set(key("QuestPluginV2", "item_version"), PersistentDataType.INTEGER, 2)
        Mockito.verify(mock.container).set(
            Mockito.eq(key("QuestPluginV2", "item_instance")),
            Mockito.eq(PersistentDataType.STRING),
            Mockito.anyString()
        )
    }

    @Test
    fun `legacy library tags remain readable`() {
        val identifier = ShimeItemIdentifier(plugin("QuestPluginV2"), plugin("shimepluginlibrary"))
        val mock = item()
        val instance = UUID.randomUUID()
        val owner = UUID.randomUUID()
        Mockito.`when`(
            mock.container.get(key("shimepluginlibrary", "item_id"), PersistentDataType.STRING)
        ).thenReturn("legacy-sword")
        Mockito.`when`(
            mock.container.get(key("shimepluginlibrary", "item_version"), PersistentDataType.INTEGER)
        ).thenReturn(3)
        Mockito.`when`(
            mock.container.get(key("shimepluginlibrary", "item_instance"), PersistentDataType.STRING)
        ).thenReturn(instance.toString())
        Mockito.`when`(
            mock.container.get(key("shimepluginlibrary", "item_owner"), PersistentDataType.STRING)
        ).thenReturn(owner.toString())

        assertEquals("legacy-sword", identifier.idOf(mock.item))
        assertEquals(3, identifier.versionOf(mock.item))
        assertEquals(instance, identifier.instanceIdOf(mock.item))
        assertEquals(owner, identifier.ownerOf(mock.item))
    }

    @Test
    fun `retagging keeps the legacy instance id when requested`() {
        val identifier = ShimeItemIdentifier(plugin("QuestPluginV2"), plugin("shimepluginlibrary"))
        val mock = item()
        val instance = UUID.randomUUID()
        Mockito.`when`(
            mock.container.get(key("shimepluginlibrary", "item_instance"), PersistentDataType.STRING)
        ).thenReturn(instance.toString())

        identifier.tag(mock.item, "sword", 2, newInstance = false)

        Mockito.verify(mock.container).set(
            key("QuestPluginV2", "item_instance"),
            PersistentDataType.STRING,
            instance.toString()
        )
    }

    @Test
    fun `entity tags are written under the consumer namespace`() {
        val service = ShimeEntityService(plugin("QuestPluginV2"), plugin("shimepluginlibrary"))
        val mock = entity()

        service.tag(mock.entity, "boss")

        Mockito.verify(mock.container).set(key("QuestPluginV2", "entity_tag"), PersistentDataType.STRING, "boss")
    }

    @Test
    fun `legacy entity tags remain readable`() {
        val service = ShimeEntityService(plugin("QuestPluginV2"), plugin("shimepluginlibrary"))
        val mock = entity()
        val owner = UUID.randomUUID()
        Mockito.`when`(
            mock.container.get(key("shimepluginlibrary", "entity_tag"), PersistentDataType.STRING)
        ).thenReturn("legacy-boss")
        Mockito.`when`(
            mock.container.get(key("shimepluginlibrary", "entity_owner"), PersistentDataType.STRING)
        ).thenReturn(owner.toString())

        assertEquals("legacy-boss", service.tagOf(mock.entity))
        assertEquals(owner, service.ownerOf(mock.entity))
    }
}
