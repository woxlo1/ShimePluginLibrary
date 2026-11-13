package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * 安定したカスタムアイテム識別タグの読み書きを行います。
 *
 * 識別子、定義バージョン、インスタンス ID、所有者はすべて永続データコンテナへ保存されます。表示名や Lore は
 * 利用者が自由に変更できるため、識別には使用しません。インスタンス ID は生成時に 1 度だけ付与され、複製された
 * アイテムは同じ ID を共有します。これを利用して重複生成を検出できます。
 *
 * 新しいタグは [plugin] の名前空間へ書き込みます。[legacyPlugin] を指定すると、1.1.0 以前に
 * `shimepluginlibrary` 名前空間で付与されたタグも読み取れます。既存タグは削除せず、そのまま残します。
 */
class ShimeItemIdentifier(
    private val plugin: JavaPlugin,
    private val legacyPlugin: JavaPlugin? = null
) {
    private val idKey = NamespacedKey(plugin, "item_id")
    private val versionKey = NamespacedKey(plugin, "item_version")
    private val instanceKey = NamespacedKey(plugin, "item_instance")
    private val ownerKey = NamespacedKey(plugin, "item_owner")

    private val legacyKeys = legacyPlugin
        ?.takeIf { it.name != plugin.name }
        ?.let { LegacyKeys(it) }

    private class LegacyKeys(plugin: JavaPlugin) {
        val id = NamespacedKey(plugin, "item_id")
        val version = NamespacedKey(plugin, "item_version")
        val instance = NamespacedKey(plugin, "item_instance")
        val owner = NamespacedKey(plugin, "item_owner")
    }

    /**
     * アイテムへ識別情報を付与する。
     *
     * @param version 定義のバージョン。0 以下は 1 として扱う。
     * @param newInstance true の場合、既存のインスタンス ID を新しい UUID で置き換える。
     */
    @JvmOverloads
    fun tag(item: ItemStack, id: String, version: Int = 1, newInstance: Boolean = true): ItemStack {
        require(id.isNotBlank()) { "Item id must not be blank." }
        val meta = item.itemMeta ?: return item
        val container = meta.persistentDataContainer
        container.set(idKey, PersistentDataType.STRING, id)
        container.set(versionKey, PersistentDataType.INTEGER, version.coerceAtLeast(1))
        if (newInstance || container.get(instanceKey, PersistentDataType.STRING) == null) {
            val existing = if (newInstance) {
                null
            } else {
                container.get(instanceKey, PersistentDataType.STRING)
                    ?: legacyKeys?.let { container.get(it.instance, PersistentDataType.STRING) }
            }
            container.set(instanceKey, PersistentDataType.STRING, existing ?: UUID.randomUUID().toString())
        }
        item.itemMeta = meta
        return item
    }

    fun idOf(item: ItemStack?): String? {
        val meta = item?.itemMeta ?: return null
        val container = meta.persistentDataContainer
        return container.get(idKey, PersistentDataType.STRING)
            ?: legacyKeys?.let { container.get(it.id, PersistentDataType.STRING) }
    }

    fun isCustom(item: ItemStack?): Boolean = idOf(item) != null

    /** 定義バージョン。タグがない場合は 0、タグはあるがバージョンがない場合は 1 を返す。 */
    fun versionOf(item: ItemStack?): Int {
        val meta = item?.itemMeta ?: return 0
        val container = meta.persistentDataContainer
        val hasId = container.get(idKey, PersistentDataType.STRING) != null ||
            legacyKeys?.let { container.get(it.id, PersistentDataType.STRING) } != null
        if (!hasId) {
            return 0
        }
        return container.get(versionKey, PersistentDataType.INTEGER)
            ?: legacyKeys?.let { container.get(it.version, PersistentDataType.INTEGER) }
            ?: 1
    }

    /** 生成時に付与されたインスタンス ID。タグがない場合は null。 */
    fun instanceIdOf(item: ItemStack?): UUID? {
        val meta = item?.itemMeta ?: return null
        val container = meta.persistentDataContainer
        val raw = container.get(instanceKey, PersistentDataType.STRING)
            ?: legacyKeys?.let { container.get(it.instance, PersistentDataType.STRING) }
            ?: return null
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** 所有者 UUID。未設定または不正な値の場合は null。 */
    fun ownerOf(item: ItemStack?): UUID? {
        val meta = item?.itemMeta ?: return null
        val container = meta.persistentDataContainer
        val raw = container.get(ownerKey, PersistentDataType.STRING)
            ?: legacyKeys?.let { container.get(it.owner, PersistentDataType.STRING) }
            ?: return null
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** 所有者を設定する。null を渡すと所有者を削除する。 */
    fun setOwner(item: ItemStack, owner: UUID?): ItemStack {
        val meta = item.itemMeta ?: return item
        if (owner == null) {
            meta.persistentDataContainer.remove(ownerKey)
            legacyKeys?.let { meta.persistentDataContainer.remove(it.owner) }
        } else {
            meta.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, owner.toString())
        }
        item.itemMeta = meta
        return item
    }

    fun key(): NamespacedKey = idKey
}
