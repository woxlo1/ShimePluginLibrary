package shimeserver.shimepluginlibrary.shimebootstrap

import shimeserver.shimepluginlibrary.ShimePlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeLibrary
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandManager
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandRegistration
import shimeserver.shimepluginlibrary.shimeconfig.ShimeConfig
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimenms.ShimeNmsManager
import java.util.concurrent.TimeUnit

/**
 * メインプラグインの起動と停止を統括する。
 *
 * この bootstrap は自身が生成するすべての長寿命オブジェクトを所有する。メインクラスは Paper のライフサイクル
 * コールバックを転送するだけなので、[ShimePlugin] を小さく保て、起動失敗からも復旧できる: [disable] が
 * 失敗前に生成されたすべてのものを解放する。
 */
class ShimePluginBootstrap(private val plugin: ShimePlugin) {
    private var library: ShimeLibrary? = null
    private var adminCommand: ShimeCommandRegistration? = null

    @Volatile
    private var enabled = false

    /** 初期化済みのライブラリを返す。起動前と停止後は `null` を返す。 */
    fun libraryOrNull(): ShimeLibrary? = library

    /**
     * ライブラリを起動する。
     *
     * このメソッドは意図的に冪等ではない: すでに有効な bootstrap を有効化するのはプログラミング上の
     * 誤りであり、[IllegalStateException] をスローする。
     */
    fun enable() {
        check(!enabled) { "ShimePluginBootstrap is already enabled." }
        val startedAt = System.nanoTime()
        val lib = ShimeLibrary(plugin)
        library = lib

        val config = lib.configManager.create("config", "config.yml", loadNow = true)
        configure(lib, config)
        ShimePluginDatabaseLoader.load(plugin, config, lib)

        val commands = ShimeCommandManager(plugin, lib.logger, lib.scheduler)
        lib.registerService(commands)

        val nms = ShimeNmsManager(lib.logger)
        lib.registerService(nms)

        lib.initialize()
        enabled = true

        try {
            adminCommand = ShimePluginAdminCommand.register(plugin, lib, commands, nms)
        } catch (throwable: Throwable) {
            lib.logger.error(
                ShimeLogCategory.COMMAND,
                "The administration command could not be registered; the library remains available.",
                throwable
            )
        }

        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        lib.logger.info(
            ShimeLogCategory.CORE,
            "ShimePluginLibrary ${plugin.pluginMeta.version} enabled in ${elapsedMillis}ms. " +
                "Services: ${lib.services.names().joinToString().ifBlank { "none" }}. " +
                "NMS available: ${nms.isAvailable()}."
        )
    }

    /**
     * ライブラリを停止し、所有するすべてのリソースを解放する。
     *
     * 部分的な起動の後でも、複数回呼び出しても安全である。
     */
    fun disable() {
        adminCommand?.unregister()
        adminCommand = null
        val lib = library
        library = null
        enabled = false
        if (lib != null) {
            try {
                lib.shutdown()
            } catch (throwable: Throwable) {
                plugin.logger.log(
                    java.util.logging.Level.SEVERE,
                    "ShimePluginLibrary shutdown failed.",
                    throwable
                )
            }
        }
    }

    private fun configure(library: ShimeLibrary, config: ShimeConfig) {
        library.logger.setDebugEnabled(config.getBoolean("debug", false))
        library.messageManager.configure(
            config.getString("language", "en_US") ?: "en_US",
            config.getBoolean("use-prefix", true)
        )
        library.logger.debug(
            ShimeLogCategory.CONFIG,
            "Loaded configuration '${config.name}' (version ${config.version()})."
        )
    }
}
