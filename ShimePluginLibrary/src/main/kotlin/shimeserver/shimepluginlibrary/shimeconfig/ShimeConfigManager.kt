package shimeserver.shimepluginlibrary.shimeconfig

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeexception.ShimeConfigurationException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.concurrent.ConcurrentHashMap

/**
 * 設定ファイルのレジストリ。各マネージャーはこのサービスを通じて自身の設定を作成するため、
 * リロードや保存の操作を 1 か所から統制できる。
 */
class ShimeConfigManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger
) : ShimeAbstractService(SERVICE_NAME) {
    private val configs = ConcurrentHashMap<String, ShimeConfig>()
    private val schemas = ConcurrentHashMap<String, ShimeConfigSchema>()

    override fun onInitialize(context: ShimeContext) {
        // 設定はライブラリおよび機能マネージャーによって登録される。
    }

    override fun onShutdown() {
        saveAll()
    }

    fun create(name: String, resourcePath: String? = "$name.yml", loadNow: Boolean = true): ShimeConfig {
        val config = ShimeConfig(plugin, name, resourcePath, logger)
        if (loadNow) {
            config.load()
        }
        register(config)
        return config
    }

    fun register(config: ShimeConfig, replace: Boolean = false) {
        val previous = configs.put(config.name, config)
        if (previous != null && !replace) {
            configs[config.name] = previous
            throw ShimeConfigurationException(
                "Configuration '${config.name}' is already registered. Use replace=true only when intentional."
            )
        }
    }

    fun get(name: String): ShimeConfig? = configs[name]

    fun require(name: String): ShimeConfig {
        return get(name) ?: throw ShimeConfigurationException(
            "Configuration '$name' is not registered. Available configurations: " +
                configs.keys.sorted().joinToString().ifBlank { "none" } + "."
        )
    }

    fun reloadAll() {
        configs.values.forEach { config ->
            try {
                config.reload()
            } catch (exception: Exception) {
                logger.error(ShimeLogCategory.CONFIG, "Could not reload configuration '${config.name}'.", exception)
            }
        }
    }

    fun saveAll() {
        configs.values.forEach { config ->
            try {
                config.save()
            } catch (exception: Exception) {
                logger.error(ShimeLogCategory.CONFIG, "Could not save configuration '${config.name}'.", exception)
            }
        }
    }

    fun names(): List<String> = configs.keys.sorted()

    /**
     * 設定ファイルに対応するスキーマを登録する。
     *
     * スキーマは設定ファイルの登録前でも登録できます。設定がまだ存在しない場合、[validate] は
     * 明確な例外をスローします。
     */
    fun registerSchema(configName: String, schema: ShimeConfigSchema, replace: Boolean = false) {
        require(configName.isNotBlank()) { "Configuration name must not be blank." }
        if (replace) {
            schemas[configName] = schema
            return
        }
        val previous = schemas.putIfAbsent(configName, schema)
        if (previous != null) {
            throw ShimeConfigurationException(
                "Schema for configuration '$configName' is already registered. Use replace=true only when intentional."
            )
        }
    }

    fun schema(configName: String): ShimeConfigSchema? = schemas[configName]

    /**
     * 指定した設定をスキーマで検証する。
     *
     * @throws ShimeConfigurationException 設定またはスキーマが登録されていない場合。
     */
    fun validate(configName: String): ShimeConfigValidationReport {
        val config = require(configName)
        val configSchema = schema(configName) ?: throw ShimeConfigurationException(
            "No schema is registered for configuration '$configName'."
        )
        val report = configSchema.validate(config)
        if (!report.isValid) {
            logger.warn(ShimeLogCategory.CONFIG, report.summary())
        }
        return report
    }

    /** 登録済みのすべてのスキーマで検証する。設定が存在しないスキーマはスキップされる。 */
    fun validateAll(): Map<String, ShimeConfigValidationReport> {
        val reports = LinkedHashMap<String, ShimeConfigValidationReport>()
        schemas.keys.sorted().forEach { configName ->
            val config = configs[configName]
            if (config == null) {
                logger.warn(ShimeLogCategory.CONFIG, "Schema '$configName' has no registered configuration.")
                return@forEach
            }
            val report = schemas.getValue(configName).validate(config)
            if (!report.isValid) {
                logger.warn(ShimeLogCategory.CONFIG, report.summary())
            }
            reports[configName] = report
        }
        return reports
    }

    /** スキーマが登録されている設定名の一覧。 */
    fun schemaNames(): List<String> = schemas.keys.sorted()

    companion object {
        const val SERVICE_NAME = "config"
    }
}
