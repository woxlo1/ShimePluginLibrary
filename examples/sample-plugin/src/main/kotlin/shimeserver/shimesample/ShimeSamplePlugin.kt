package shimeserver.shimesample

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeLibrary
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandManager
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandRegistration
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandResult
import shimeserver.shimepluginlibrary.shimecommand.shimeCommand
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory

/**
 * ShimePluginLibrary を利用する最小構成のサンプルプラグイン。
 *
 * ライブラリの初期化は ShimePluginLibrary 本体が行うため、ここでは `ShimeLibrary.getOrNull()` で
 * 初期化済みインスタンスを取得し、コマンド API だけを登録する。
 */
class ShimeSamplePlugin : JavaPlugin() {
    private var registration: ShimeCommandRegistration? = null

    override fun onEnable() {
        val library = ShimeLibrary.getOrNull()
        if (library == null) {
            logger.severe("ShimePluginLibrary is not available. Disabling ShimeSamplePlugin.")
            server.pluginManager.disablePlugin(this)
            return
        }

        val commands = ShimeCommandManager(this, library.logger, library.scheduler)
        val root = shimeCommand("shimesample") {
            permission("shimesample.use")
            description("ShimeSamplePlugin のサンプルコマンド。")
            executes { context, _ ->
                context.sender.sendMessage("Hello from ShimeSamplePlugin!")
                ShimeCommandResult.SilentSuccess
            }
        }

        try {
            registration = commands.register(root)
            library.logger.info(ShimeLogCategory.CORE, "ShimeSamplePlugin enabled.")
        } catch (throwable: Throwable) {
            library.logger.error(
                ShimeLogCategory.COMMAND,
                "The sample command could not be registered.",
                throwable
            )
        }
    }

    override fun onDisable() {
        registration?.unregister()
        registration = null
    }
}
