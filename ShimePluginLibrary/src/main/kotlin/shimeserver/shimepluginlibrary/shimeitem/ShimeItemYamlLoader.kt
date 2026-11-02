package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation
import java.io.File

/**
 * YAML からアイテムテンプレートを読み込むローダー。
 *
 * ファイルは次の形式を想定します。
 *
 * ```yaml
 * items:
 *   example_sword:
 *     material: DIAMOND_SWORD
 *     name: "<aqua>Example Sword</aqua>"
 *     lore:
 *       - "<gray>A declarative item.</gray>"
 *     enchantments:
 *       sharpness: 3
 *     flags:
 *       - HIDE_ATTRIBUTES
 * ```
 *
 * 読み込みはテンプレートの生成だけを行い、レジストリへは登録しません。登録は
 * [ShimeItemManager.registerTemplates] を通じて明示的に行います。
 */
object ShimeItemYamlLoader {
    /** YAML ファイルを読み込む。 */
    @JvmStatic
    fun load(file: File): Map<String, ShimeItemTemplate> {
        val yaml = YamlConfiguration.loadConfiguration(file)
        return load(yaml)
    }

    /** 読み込み済みの YAML から `items` セクションを取得する。 */
    @JvmStatic
    fun load(yaml: YamlConfiguration): Map<String, ShimeItemTemplate> {
        val section = yaml.getConfigurationSection("items")
            ?: throw ShimeValidationException("Item file must contain an 'items' section.")
        return loadSection(section)
    }

    /**
     * `items` セクションを読み込む。
     *
     * @throws ShimeValidationException 定義 ID が安全でない、またはテンプレートが不正な場合。
     */
    @JvmStatic
    fun loadSection(section: ConfigurationSection): Map<String, ShimeItemTemplate> {
        val templates = LinkedHashMap<String, ShimeItemTemplate>()
        section.getKeys(false).forEach { id ->
            ShimeValidation.requireSafeKey(id, "item definition id")
            val itemSection = section.getConfigurationSection(id)
                ?: throw ShimeValidationException("Item definition '$id' must be a section.")
            templates[id] = try {
                ShimeItemTemplate.fromSection(itemSection)
            } catch (exception: ShimeValidationException) {
                throw ShimeValidationException("Invalid item definition '$id': ${exception.message}", exception)
            }
        }
        return templates
    }
}
