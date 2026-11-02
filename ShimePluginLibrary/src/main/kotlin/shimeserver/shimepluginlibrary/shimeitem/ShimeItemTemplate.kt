package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException
import java.util.Locale

/**
 * 表示名、Lore、エンチャントなどを宣言的に保持するアイテムテンプレート。
 *
 * テンプレートは不変であり、[build] を呼ぶたびに独立した [ItemStack] を生成します。識別タグは
 * [ShimeItemManager] が付与するため、テンプレート自体はカスタムアイテム ID を持ちません。
 */
data class ShimeItemTemplate(
    val material: Material,
    val amount: Int = 1,
    val displayName: String? = null,
    val lore: List<String> = emptyList(),
    val enchantments: Map<Enchantment, Int> = emptyMap(),
    val flags: Set<ItemFlag> = emptySet(),
    val customModelData: Int? = null,
    val unbreakable: Boolean = false,
    val damage: Int? = null,
    val persistentData: Map<String, String> = emptyMap()
) {
    init {
        require(material != Material.AIR) { "Item template material must not be air." }
        require(amount in 1..64) { "Item template amount must be between 1 and 64." }
        enchantments.forEach { (_, level) ->
            require(level > 0) { "Enchantment levels must be positive." }
        }
        require(damage == null || damage >= 0) { "Item damage must not be negative." }
        persistentData.keys.forEach { key ->
            require(key.isNotBlank()) { "Persistent data keys must not be blank." }
        }
    }

    /** テンプレートから新しい [ItemStack] を生成する。 */
    fun build(plugin: JavaPlugin): ItemStack {
        val builder = ShimeItemBuilder(plugin, material, amount)
        displayName?.let { builder.name(it) }
        lore.forEach { builder.addLore(it) }
        enchantments.forEach { (enchantment, level) -> builder.enchant(enchantment, level) }
        if (flags.isNotEmpty()) {
            builder.flag(*flags.toTypedArray())
        }
        customModelData?.let { builder.customModelData(it) }
        if (unbreakable) {
            builder.unbreakable(true)
        }
        damage?.let { builder.damage(it) }
        persistentData.forEach { (key, value) -> builder.persistentString(key, value) }
        return builder.build()
    }

    /** 個数だけを変えたテンプレートを返す。 */
    fun withAmount(amount: Int): ShimeItemTemplate = copy(amount = amount)

    /** テンプレートを YAML へ書き戻せるマップへ変換する。 */
    fun toMap(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["material"] = material.key.toString()
        map["amount"] = amount
        displayName?.let { map["name"] = it }
        if (lore.isNotEmpty()) {
            map["lore"] = lore
        }
        if (enchantments.isNotEmpty()) {
            map["enchantments"] = enchantments.mapKeys { it.key.key.toString() }
        }
        if (flags.isNotEmpty()) {
            map["flags"] = flags.map { it.name }
        }
        customModelData?.let { map["custom-model-data"] = it }
        if (unbreakable) {
            map["unbreakable"] = true
        }
        damage?.let { map["damage"] = it }
        if (persistentData.isNotEmpty()) {
            map["persistent-data"] = persistentData
        }
        return map
    }

    companion object {
        /**
         * YAML セクションからテンプレートを読み込む。
         *
         * @throws ShimeValidationException 必須項目の欠落、未知の Material、不正なフラグなどがある場合。
         */
        @JvmStatic
        fun fromSection(section: ConfigurationSection): ShimeItemTemplate {
            val materialName = section.getString("material")
                ?: throw ShimeValidationException("Item template is missing 'material'.")
            val material = Material.matchMaterial(materialName)
                ?: throw ShimeValidationException("Unknown material '$materialName'.")

            val enchantments = LinkedHashMap<Enchantment, Int>()
            section.getConfigurationSection("enchantments")?.let { enchantmentSection ->
                enchantmentSection.getKeys(false).forEach { key ->
                    val level = enchantmentSection.getInt(key)
                    enchantments[resolveEnchantment(key)] = level
                }
            }

            val flags = section.getStringList("flags").map { raw ->
                try {
                    ItemFlag.valueOf(raw.uppercase(Locale.ROOT))
                } catch (_: IllegalArgumentException) {
                    throw ShimeValidationException("Unknown item flag '$raw'.")
                }
            }.toSet()

            val persistentData = LinkedHashMap<String, String>()
            section.getConfigurationSection("persistent-data")?.let { dataSection ->
                dataSection.getKeys(false).forEach { key ->
                    dataSection.getString(key)?.let { persistentData[key] = it }
                }
            }

            return ShimeItemTemplate(
                material = material,
                amount = if (section.contains("amount")) section.getInt("amount") else 1,
                displayName = section.getString("name")?.takeIf { it.isNotBlank() },
                lore = section.getStringList("lore"),
                enchantments = enchantments,
                flags = flags,
                customModelData = if (section.contains("custom-model-data")) {
                    section.getInt("custom-model-data")
                } else {
                    null
                },
                unbreakable = section.getBoolean("unbreakable", false),
                damage = if (section.contains("damage")) section.getInt("damage") else null,
                persistentData = persistentData
            )
        }

        private fun resolveEnchantment(key: String): Enchantment {
            val namespaced = NamespacedKey.fromString(key.lowercase(Locale.ROOT))
                ?: throw ShimeValidationException("Invalid enchantment key '$key'.")
            return Registry.ENCHANTMENT.get(namespaced)
                ?: throw ShimeValidationException("Unknown enchantment '$key'.")
        }
    }
}
