package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/** 安定したカスタムアイテム識別タグの読み書きを行います。 */
class ShimeItemIdentifier(private val plugin: JavaPlugin) {
    private val key = NamespacedKey(plugin, "item_id")

    fun tag(item: ItemStack, id: String): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, id)
        item.itemMeta = meta
        return item
    }

    fun idOf(item: ItemStack?): String? {
        val meta = item?.itemMeta ?: return null
        return meta.persistentDataContainer.get(key, PersistentDataType.STRING)
    }

    fun isCustom(item: ItemStack?): Boolean = idOf(item) != null

    fun key(): NamespacedKey = key
}
