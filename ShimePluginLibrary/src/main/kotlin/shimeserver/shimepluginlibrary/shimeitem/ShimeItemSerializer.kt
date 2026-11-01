package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException

/** Bukkit の安定したシリアライゼーション形式に基づく、安全な ItemStack マップシリアライゼーション。 */
object ShimeItemSerializer {
    fun toMap(item: ItemStack): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return item.serialize() as Map<String, Any?>
    }

    fun fromMap(map: Map<String, Any?>): ItemStack {
        return try {
            @Suppress("UNCHECKED_CAST")
            ItemStack.deserialize(map as Map<String, Any>)
        } catch (exception: Exception) {
            throw ShimeValidationException("Could not deserialize item data.", exception)
        }
    }

    fun toYaml(item: ItemStack): String {
        val yaml = YamlConfiguration()
        toMap(item).forEach { (key, value) -> yaml.set(key, value) }
        return yaml.saveToString()
    }

    fun fromYaml(yamlText: String): ItemStack {
        val yaml = YamlConfiguration()
        try {
            yaml.loadFromString(yamlText)
            @Suppress("UNCHECKED_CAST")
            return fromMap(yaml.getValues(false) as Map<String, Any?>)
        } catch (exception: Exception) {
            throw ShimeValidationException("Could not read item YAML.", exception)
        }
    }
}
