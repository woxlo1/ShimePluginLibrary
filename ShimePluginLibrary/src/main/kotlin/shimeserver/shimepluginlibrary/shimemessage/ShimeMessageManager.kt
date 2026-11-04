package shimeserver.shimepluginlibrary.shimemessage

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import shimeserver.shimepluginlibrary.shimeutil.ShimeText
import java.io.File
import java.time.Duration
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 言語ファイルの読み込みとメッセージ送信を行う。
 *
 * キーはフラット形式 (`command.no-permission`) またはネストした YAML パス (`command.no-permission`)
 * のいずれかである。存在しないキーは一度だけ報告され、デフォルトロケールにフォールバックした後、
 * 最終的にはキー自体を返す。
 */
class ShimeMessageManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger,
    private val scheduler: ShimeScheduler
) : ShimeAbstractService(SERVICE_NAME) {
    private val languages = ConcurrentHashMap<String, Map<String, String>>()
    private val missingKeys = ConcurrentHashMap.newKeySet<String>()
    private val bossBars = ConcurrentHashMap<UUID, BossBar>()
    private val templates = ConcurrentHashMap<String, ConcurrentHashMap<String, ShimeMessageTemplate>>()
    private val componentCache = object : LinkedHashMap<String, Component>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Component>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    @Volatile
    private var defaultLocaleCode: String = "en_US"

    @Volatile
    private var prefixEnabled: Boolean = true

    override fun onInitialize(context: ShimeContext) {
        reload()
    }

    override fun onShutdown() {
        bossBars.forEach { (uuid, bar) ->
            plugin.server.getPlayer(uuid)?.hideBossBar(bar)
        }
        bossBars.clear()
    }

    fun configure(defaultLocale: String, usePrefix: Boolean) {
        defaultLocaleCode = normalizeLocale(defaultLocale)
        prefixEnabled = usePrefix
        clearCache()
    }

    fun reload() {
        val directory = File(plugin.dataFolder, "messages")
        if (!directory.exists() && !directory.mkdirs()) {
            logger.error(ShimeLogCategory.MESSAGE, "Could not create message directory '${directory.path}'.")
            return
        }
        saveDefaultLanguage("messages/en_US.yml", "en_US.yml")
        saveDefaultLanguage("messages/ja_JP.yml", "ja_JP.yml")

        val loaded = HashMap<String, Map<String, String>>()
        directory.listFiles { file -> file.isFile && file.name.endsWith(".yml") }?.forEach { file ->
            val locale = normalizeLocale(file.nameWithoutExtension)
            try {
                val yaml = YamlConfiguration.loadConfiguration(file)
                val values = HashMap<String, String>()
                yaml.getKeys(true).forEach { key ->
                    val value = yaml.getString(key)
                    if (value != null) {
                        values[key] = value
                    }
                }
                loaded[locale] = values
            } catch (exception: Exception) {
                logger.error(ShimeLogCategory.MESSAGE, "Could not load language file '${file.name}'.", exception)
            }
        }
        if (loaded.isEmpty()) {
            logger.warn(ShimeLogCategory.MESSAGE, "No language files were loaded; messages will use their keys.")
        }
        languages.clear()
        languages.putAll(loaded)
        missingKeys.clear()
        clearCache()
        logger.info(ShimeLogCategory.MESSAGE, "Loaded ${languages.size} language file(s).")
    }

    fun defaultLocale(): String = defaultLocaleCode

    fun locales(): Set<String> = languages.keys.toSet()

    fun raw(locale: String, key: String): String? {
        val normalized = normalizeLocale(locale)
        languages[normalized]?.get(key)?.let { return it }
        template(normalized, key)?.let { return it.defaultText }
        val languageOnly = normalized.substringBefore('_')
        languages[languageOnly]?.get(key)?.let { return it }
        template(languageOnly, key)?.let { return it.defaultText }
        languages[defaultLocaleCode]?.get(key)?.let { return it }
        template(defaultLocaleCode, key)?.let { return it.defaultText }
        if (missingKeys.add("$normalized:$key")) {
            logger.warn(ShimeLogCategory.MESSAGE, "Missing message '$key' for locale '$normalized'.")
        }
        return null
    }

    /** [locale] に登録したテンプレートを返す。 */
    fun template(locale: String, key: String): ShimeMessageTemplate? {
        return templates[normalizeLocale(locale)]?.get(key)
    }

    /** テンプレートを既定ロケールに登録する。 */
    @JvmOverloads
    fun registerTemplate(template: ShimeMessageTemplate, locale: String = defaultLocaleCode) {
        registerTemplate(locale, template)
    }

    /** テンプレートを指定ロケールに登録する。 */
    fun registerTemplate(locale: String, template: ShimeMessageTemplate) {
        templates.computeIfAbsent(normalizeLocale(locale)) { ConcurrentHashMap() }[template.key] = template
        clearCache()
    }

    /** 登録済みテンプレートを解除する。 */
    @JvmOverloads
    fun unregisterTemplate(key: String, locale: String = defaultLocaleCode): Boolean {
        val removed = templates[normalizeLocale(locale)]?.remove(key) != null
        if (removed) {
            clearCache()
        }
        return removed
    }

    /** 登録済みテンプレートをすべて解除する。 */
    fun clearTemplates() {
        templates.clear()
        clearCache()
    }

    fun text(locale: String, key: String, placeholders: Map<String, String> = emptyMap()): String {
        val template = raw(locale, key) ?: key
        val replaced = ShimeText.replace(template, placeholders)
        return if (prefixEnabled && key != "prefix") {
            val prefix = raw(locale, "prefix").orEmpty()
            prefix + replaced
        } else {
            replaced
        }
    }

    fun component(locale: String, key: String, placeholders: Map<String, String> = emptyMap()): Component {
        val normalized = normalizeLocale(locale)
        val cacheKey = buildString {
            append(normalized).append('\u0000').append(key).append('\u0000').append(prefixEnabled)
            placeholders.entries.sortedBy { it.key }.forEach { entry ->
                append('\u0000').append(entry.key).append('=').append(entry.value)
            }
        }
        synchronized(componentCache) {
            componentCache[cacheKey]?.let { return it }
        }
        val component = ShimeText.component(text(normalized, key, placeholders))
        synchronized(componentCache) {
            componentCache[cacheKey] = component
        }
        return component
    }

    fun send(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, String> = emptyMap(),
        locale: String? = null
    ) {
        val effectiveLocale = locale ?: if (sender is Player) sender.locale().toString() else defaultLocaleCode
        sender.sendMessage(component(effectiveLocale, key, placeholders))
    }

    fun sendActionBar(player: Player, key: String, placeholders: Map<String, String> = emptyMap()) {
        player.sendActionBar(component(player.locale().toString(), key, placeholders))
    }

    fun sendTitle(
        player: Player,
        titleKey: String,
        subtitleKey: String? = null,
        placeholders: Map<String, String> = emptyMap()
    ) {
        val locale = player.locale().toString()
        val title = component(locale, titleKey, placeholders)
        val subtitle = subtitleKey?.let { component(locale, it, placeholders) } ?: Component.empty()
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
            Duration.ofMillis(250),
            Duration.ofSeconds(2),
            Duration.ofMillis(500)
        )))
    }

    fun sendBossBar(
        player: Player,
        key: String,
        durationMillis: Long = 5_000,
        progress: Float = 1.0f,
        placeholders: Map<String, String> = emptyMap()
    ) {
        val bar = BossBar.bossBar(
            component(player.locale().toString(), key, placeholders),
            progress.coerceIn(0.0f, 1.0f),
            BossBar.Color.WHITE,
            BossBar.Overlay.PROGRESS
        )
        bossBars.remove(player.uniqueId)?.let(player::hideBossBar)
        bossBars[player.uniqueId] = bar
        player.showBossBar(bar)
        scheduler.runLater((durationMillis / 50).coerceAtLeast(1)) {
            if (bossBars.remove(player.uniqueId, bar)) {
                player.hideBossBar(bar)
            }
        }
    }

    fun hasMessage(locale: String, key: String): Boolean {
        val normalized = normalizeLocale(locale)
        if (languages[normalized]?.containsKey(key) == true) {
            return true
        }
        if (template(normalized, key) != null) {
            return true
        }
        return template(normalized.substringBefore('_'), key) != null
    }

    /** サウンド付きでメッセージを送信する。音は送信者がプレイヤーの場合のみ再生される。 */
    @JvmOverloads
    fun sendWithSound(
        sender: CommandSender,
        key: String,
        sound: Sound,
        placeholders: Map<String, String> = emptyMap(),
        locale: String? = null
    ) {
        send(sender, key, placeholders, locale)
        if (sender is Player) {
            sender.playSound(sender.location, sound, 1.0f, 1.0f)
        }
    }

    /**
     * メッセージの存在、プレースホルダー、MiniMessage 形式を検証する。
     *
     * @param supplied 呼び出し側が実際に渡すプレースホルダー名。
     * @param required 追加で必須とするプレースホルダー名。テンプレートの宣言と統合される。
     */
    @JvmOverloads
    fun validate(
        locale: String,
        key: String,
        supplied: Set<String> = emptySet(),
        required: Set<String> = emptySet()
    ): ShimeMessageValidationReport {
        val normalized = normalizeLocale(locale)
        val raw = raw(normalized, key)
            ?: return ShimeMessageValidationReport(
                normalized,
                key,
                listOf(ShimeMessageIssue(ShimeMessageIssueType.MISSING_KEY, "Message '$key' is not defined."))
            )
        val declared = required + (template(normalized, key)?.placeholders() ?: extractPlaceholders(raw))
        val issues = ArrayList<ShimeMessageIssue>()
        (declared - supplied).sorted().forEach { placeholder ->
            issues += ShimeMessageIssue(
                ShimeMessageIssueType.MISSING_PLACEHOLDER,
                "Message '$key' requires placeholder '{$placeholder}'.",
                placeholder
            )
        }
        val sample = declared.associateWith { "sample" }
        try {
            MiniMessage.miniMessage().deserialize(ShimeText.replace(raw, sample))
        } catch (exception: RuntimeException) {
            issues += ShimeMessageIssue(
                ShimeMessageIssueType.FORMAT_ERROR,
                "Message '$key' contains invalid MiniMessage: ${exception.message}",
                null
            )
        }
        return ShimeMessageValidationReport(normalized, key, issues)
    }

    /** メッセージコンポーネントのキャッシュを消去する。 */
    fun clearCache() {
        synchronized(componentCache) {
            componentCache.clear()
        }
    }

    /** キャッシュされているメッセージ数を返す。 */
    fun cachedMessages(): Int {
        synchronized(componentCache) {
            return componentCache.size
        }
    }

    fun missingKeys(): Set<String> = missingKeys.toSet()

    private fun extractPlaceholders(template: String): Set<String> {
        val pattern = Regex("[{%]([A-Za-z0-9_.-]+)[}%]")
        return pattern.findAll(template).map { it.groupValues[1] }.toSet()
    }

    private fun saveDefaultLanguage(resourcePath: String, fileName: String) {
        val target = File(plugin.dataFolder, "messages/$fileName")
        if (target.exists()) {
            return
        }
        if (plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false)
        }
    }

    private fun normalizeLocale(input: String): String {
        val cleaned = input.trim().replace('-', '_')
        if (cleaned.isEmpty()) {
            return defaultLocaleCode
        }
        val parts = cleaned.split('_')
        return if (parts.size >= 2) {
            "${parts[0].lowercase(Locale.ROOT)}_${parts[1].uppercase(Locale.ROOT)}"
        } else {
            cleaned.lowercase(Locale.ROOT)
        }
    }

    companion object {
        const val SERVICE_NAME = "message"

        /** コンポーネントキャッシュの最大件数。 */
        const val MAX_CACHE_ENTRIES = 512
    }
}
