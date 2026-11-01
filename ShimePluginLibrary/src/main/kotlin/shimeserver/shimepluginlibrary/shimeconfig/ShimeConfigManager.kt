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

    companion object {
        const val SERVICE_NAME = "config"
    }
}
