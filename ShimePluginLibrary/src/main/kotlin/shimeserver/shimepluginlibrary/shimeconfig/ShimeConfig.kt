package shimeserver.shimepluginlibrary.shimeconfig

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeexception.ShimeConfigurationException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeutil.ShimeFiles
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * デフォルト値、検証、バックアップ、アトミック保存を備えた YAML 設定ファイル。
 *
 * ユーザーファイルが既に存在する場合でも、埋め込みリソースはデフォルト値の提供元として使われる。これにより
 * ユーザーの値を上書きせずに、アップデートで新しいキーを追加できる。
 */
class ShimeConfig(
    private val plugin: JavaPlugin,
    val name: String,
    private val resourcePath: String? = null,
    private val logger: ShimeLogger
) {
    private val lock = ReentrantLock()
    private var yaml: YamlConfiguration = YamlConfiguration()

    @Volatile
    private var loaded = false

    val file: File = File(plugin.dataFolder, if (name.endsWith(".yml", ignoreCase = true)) name else "$name.yml")

    val isLoaded: Boolean
        get() = loaded

    fun load(): ShimeConfig = lock.withLock {
        try {
            if (!file.exists()) {
                createFromResource()
            }
            val loaded = YamlConfiguration.loadConfiguration(file)
            applyEmbeddedDefaults(loaded)
            loaded.options().copyDefaults(true)
            yaml = loaded
            this.loaded = true
            logger.debug(ShimeLogCategory.CONFIG, "Loaded configuration '$name'.")
            this
        } catch (exception: ShimeConfigurationException) {
            throw exception
        } catch (exception: Exception) {
            throw ShimeConfigurationException("Could not load configuration '$name'.", exception)
        }
    }

    fun reload(): ShimeConfig = load()

    fun save() {
        lock.withLock {
            try {
                yaml.options().copyDefaults(true)
                ShimeFiles.atomicWrite(file.toPath(), yaml.saveToString())
                logger.debug(ShimeLogCategory.CONFIG, "Saved configuration '$name'.")
            } catch (exception: Exception) {
                throw ShimeConfigurationException("Could not save configuration '$name'.", exception)
            }
        }
    }

    fun backup(): File? {
        return try {
            ShimeFiles.backup(file.toPath(), File(plugin.dataFolder, "backups").toPath())?.toFile()
        } catch (exception: Exception) {
            throw ShimeConfigurationException("Could not back up configuration '$name'.", exception)
        }
    }

    fun contains(path: String): Boolean = lock.withLock { yaml.contains(path) }

    fun get(path: String): Any? = lock.withLock { yaml.get(path) }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(path: String, defaultValue: T): T = lock.withLock { yaml.get(path, defaultValue) as T }

    fun getString(path: String, defaultValue: String? = null): String? {
        return lock.withLock { yaml.getString(path, defaultValue) }
    }

    fun requireString(path: String): String {
        return getString(path)?.takeIf { it.isNotBlank() }
            ?: throw ShimeConfigurationException("Configuration '$name' is missing a non-blank value at '$path'.")
    }

    fun getInt(path: String, defaultValue: Int = 0): Int = lock.withLock { yaml.getInt(path, defaultValue) }

    fun getLong(path: String, defaultValue: Long = 0L): Long = lock.withLock { yaml.getLong(path, defaultValue) }

    fun getDouble(path: String, defaultValue: Double = 0.0): Double {
        return lock.withLock { yaml.getDouble(path, defaultValue) }
    }

    fun getBoolean(path: String, defaultValue: Boolean = false): Boolean {
        return lock.withLock { yaml.getBoolean(path, defaultValue) }
    }

    fun getStringList(path: String): List<String> = lock.withLock { yaml.getStringList(path) }

    fun getSection(path: String): ConfigurationSection? = lock.withLock { yaml.getConfigurationSection(path) }

    fun getKeys(deep: Boolean = false): Set<String> = lock.withLock { yaml.getKeys(deep) }

    fun set(path: String, value: Any?) {
        lock.withLock {
            yaml.set(path, value)
        }
    }

    fun remove(path: String) {
        lock.withLock { yaml.set(path, null) }
    }

    fun setComments(path: String, comments: List<String>?) {
        lock.withLock { yaml.setComments(path, comments) }
    }

    fun getComments(path: String): List<String>? = lock.withLock { yaml.getComments(path) }

    /** `config-version` に保存されているスキーマバージョンを返す。 */
    fun version(): Int = getInt("config-version", 1)

    /**
     * バージョン付きの移行ステップを適用し、結果を永続化する。
     * ステップは移行元のバージョンをキーとして登録する（例: `1 to 2`）。
     */
    fun migrate(targetVersion: Int, steps: Map<Int, (ConfigurationSection) -> Unit>) {
        require(targetVersion >= 1) { "Target configuration version must be at least 1." }
        lock.withLock {
            var current = yaml.getInt("config-version", 1)
            if (current >= targetVersion) {
                return
            }
            while (current < targetVersion) {
                val step = steps[current] ?: throw ShimeConfigurationException(
                    "No migration step is registered for configuration '$name' version $current."
                )
                step(yaml)
                current += 1
                yaml.set("config-version", current)
                logger.info(ShimeLogCategory.CONFIG, "Migrated configuration '$name' to version $current.")
            }
            save()
        }
    }

    /**
     * 必須の整数を読み取り、その範囲を検証する。エラーメッセージにはファイルとパスが含まれる。
     */
    fun requireIntInRange(path: String, min: Int, max: Int): Int {
        if (!contains(path)) {
            throw ShimeConfigurationException("Configuration '$name' is missing '$path'.")
        }
        val value = getInt(path)
        try {
            return ShimeValidation.requireInRange(value, "$name:$path", min, max)
        } catch (exception: Exception) {
            throw ShimeConfigurationException(exception.message ?: "Invalid value at '$path'.", exception)
        }
    }

    private fun createFromResource() {
        val parent = file.parentFile
        if (!parent.exists() && !parent.mkdirs()) {
            throw ShimeConfigurationException("Could not create configuration directory '${parent.path}'.")
        }
        val resource = resourcePath?.let { plugin.getResource(it) }
        if (resource != null) {
            plugin.saveResource(resourcePath, false)
        } else {
            ShimeFiles.atomicWrite(file.toPath(), "# Generated by ShimePluginLibrary\n")
        }
    }

    private fun applyEmbeddedDefaults(target: YamlConfiguration) {
        val path = resourcePath ?: return
        val stream = plugin.getResource(path) ?: return
        stream.use {
            val defaults = YamlConfiguration.loadConfiguration(InputStreamReader(it, StandardCharsets.UTF_8))
            target.setDefaults(defaults)
        }
    }
}
