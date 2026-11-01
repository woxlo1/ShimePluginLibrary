package shimeserver.shimepluginlibrary.shimeitem

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeutil.ShimeText

/**
 * 流暢に記述できるアイテムビルダー。
 *
 * カスタムデータは表示テキストではなく永続データコンテナに書き込まれるため、アイテムの名前を変更しても
 * その同一性を偽装することはできません。
 */
class ShimeItemBuilder(
    private val plugin: JavaPlugin,
    private val material: Material,
    private var amount: Int = 1
) {
    private var displayName: Component? = null
    private val lore = ArrayList<Component>()
    private val enchantments = LinkedHashMap<Enchantment, Int>()
    private val flags = LinkedHashSet<ItemFlag>()
    private val persistentStrings = LinkedHashMap<String, String>()
    private val persistentInts = LinkedHashMap<String, Int>()
    private val persistentLongs = LinkedHashMap<String, Long>()
    private val persistentDoubles = LinkedHashMap<String, Double>()
    private val persistentBooleans = LinkedHashMap<String, Boolean>()
    private val attributeModifiers = ArrayList<Pair<Attribute, AttributeModifier>>()
    private var customModelData: Int? = null
    private var unbreakable: Boolean = false
    private var damage: Int? = null

    fun amount(amount: Int): ShimeItemBuilder {
        require(amount in 1..64) { "Item amount must be between 1 and 64." }
        this.amount = amount
        return this
    }

    fun name(name: String): ShimeItemBuilder {
        displayName = ShimeText.component(name)
        return this
    }

    fun name(name: Component): ShimeItemBuilder {
        displayName = name
        return this
    }

    fun lore(vararg lines: String): ShimeItemBuilder {
        lines.forEach { lore += ShimeText.component(it) }
        return this
    }

    fun lore(lines: Iterable<Component>): ShimeItemBuilder {
        lore += lines
        return this
    }

    fun addLore(line: String): ShimeItemBuilder {
        lore += ShimeText.component(line)
        return this
    }

    fun enchant(enchantment: Enchantment, level: Int = 1): ShimeItemBuilder {
        require(level > 0) { "Enchantment level must be positive." }
        enchantments[enchantment] = level
        return this
    }

    fun enchant(key: String, level: Int = 1): ShimeItemBuilder {
        val enchantmentKey = NamespacedKey.fromString(key)
            ?: throw IllegalArgumentException("Invalid enchantment key '$key'.")
        val enchantment = Registry.ENCHANTMENT.get(enchantmentKey)
            ?: throw IllegalArgumentException("Unknown enchantment '$key'.")
        return enchant(enchantment, level)
    }

    fun flag(vararg itemFlags: ItemFlag): ShimeItemBuilder {
        flags += itemFlags
        return this
    }

    fun customModelData(value: Int?): ShimeItemBuilder {
        customModelData = value
        return this
    }

    fun unbreakable(value: Boolean = true): ShimeItemBuilder {
        unbreakable = value
        return this
    }

    fun damage(value: Int?): ShimeItemBuilder {
        damage = value
        return this
    }

    fun persistentString(key: String, value: String): ShimeItemBuilder {
        persistentStrings[key] = value
        return this
    }

    fun persistentInt(key: String, value: Int): ShimeItemBuilder {
        persistentInts[key] = value
        return this
    }

    fun persistentLong(key: String, value: Long): ShimeItemBuilder {
        persistentLongs[key] = value
        return this
    }

    fun persistentDouble(key: String, value: Double): ShimeItemBuilder {
        persistentDoubles[key] = value
        return this
    }

    fun persistentBoolean(key: String, value: Boolean): ShimeItemBuilder {
        persistentBooleans[key] = value
        return this
    }

    fun attribute(attribute: Attribute, amount: Double, operation: AttributeModifier.Operation): ShimeItemBuilder {
        val key = NamespacedKey(plugin, "modifier_${attribute.key.key}_${attributeModifiers.size}")
        val modifier = AttributeModifier(
            java.util.UUID.randomUUID(),
            key.toString(),
            amount,
            operation
        )
        attributeModifiers += attribute to modifier
        return this
    }

    fun build(): ItemStack {
        val item = ItemStack(material, amount)
        val meta = item.itemMeta ?: return item
        displayName?.let(meta::displayName)
        if (lore.isNotEmpty()) {
            meta.lore(lore)
        }
        enchantments.forEach { (enchantment, level) -> meta.addEnchant(enchantment, level, true) }
        if (flags.isNotEmpty()) {
            meta.addItemFlags(*flags.toTypedArray())
        }
        customModelData?.let { meta.setCustomModelData(it) }
        meta.isUnbreakable = unbreakable
        if (meta is Damageable) {
            damage?.let { meta.damage = it }
        }
        attributeModifiers.forEach { (attribute, modifier) -> meta.addAttributeModifier(attribute, modifier) }
        persistentStrings.forEach { (key, value) ->
            meta.persistentDataContainer.set(NamespacedKey(plugin, key), PersistentDataType.STRING, value)
        }
        persistentInts.forEach { (key, value) ->
            meta.persistentDataContainer.set(NamespacedKey(plugin, key), PersistentDataType.INTEGER, value)
        }
        persistentLongs.forEach { (key, value) ->
            meta.persistentDataContainer.set(NamespacedKey(plugin, key), PersistentDataType.LONG, value)
        }
        persistentDoubles.forEach { (key, value) ->
            meta.persistentDataContainer.set(NamespacedKey(plugin, key), PersistentDataType.DOUBLE, value)
        }
        persistentBooleans.forEach { (key, value) ->
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, key),
                PersistentDataType.BYTE,
                if (value) 1.toByte() else 0.toByte()
            )
        }
        item.itemMeta = meta
        return item
    }

    companion object {
        @JvmStatic
        fun of(plugin: JavaPlugin, material: Material, amount: Int = 1): ShimeItemBuilder {
            return ShimeItemBuilder(plugin, material, amount)
        }
    }
}
