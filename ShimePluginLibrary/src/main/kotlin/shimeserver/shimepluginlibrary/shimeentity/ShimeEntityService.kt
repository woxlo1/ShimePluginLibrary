package shimeserver.shimepluginlibrary.shimeentity

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.UUID

/**
 * 安定したエンティティのタグ付けと安全なライフサイクルヘルパー。
 *
 * タグは永続データコンテナに保存されるため、チャンクの保存をまたいで維持され、カスタム名によって
 * 偽装することはできない。
 */
class ShimeEntityService(
    private val plugin: JavaPlugin
) : ShimeAbstractService(SERVICE_NAME) {
    private val tagKey = NamespacedKey(plugin, "entity_tag")
    private val ownerKey = NamespacedKey(plugin, "entity_owner")

    override fun onInitialize(context: ShimeContext) {
        // ステートレスなサービス。キーは一度だけ生成される。
    }

    fun tag(entity: Entity, tag: String) {
        require(tag.isNotBlank()) { "Entity tag must not be blank." }
        val pdc = entity.persistentDataContainer
        pdc.set(tagKey, PersistentDataType.STRING, tag)
    }

    fun tagOf(entity: Entity): String? {
        return entity.persistentDataContainer.get(tagKey, PersistentDataType.STRING)
    }

    fun clearTag(entity: Entity) {
        entity.persistentDataContainer.remove(tagKey)
    }

    fun setOwner(entity: Entity, owner: UUID?) {
        val pdc = entity.persistentDataContainer
        if (owner == null) {
            pdc.remove(ownerKey)
        } else {
            pdc.set(ownerKey, PersistentDataType.STRING, owner.toString())
        }
    }

    fun ownerOf(entity: Entity): UUID? {
        return entity.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    fun findTagged(world: World, tag: String): List<Entity> {
        return world.entities.filter { tagOf(it) == tag }
    }

    fun findTagged(tag: String): List<Entity> {
        return Bukkit.getWorlds().flatMap { findTagged(it, tag) }
    }

    fun removeSafely(entity: Entity): Boolean {
        if (!entity.isValid) {
            return false
        }
        entity.remove()
        return true
    }

    companion object {
        const val SERVICE_NAME = "entity"
    }
}
