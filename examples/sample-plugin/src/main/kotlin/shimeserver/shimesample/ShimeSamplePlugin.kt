package shimeserver.shimesample

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeLibrary
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandArg
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandArgumentType
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandContext
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandDefinition
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandHandler
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandManager
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandRegistration
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandResult
import shimeserver.shimepluginlibrary.shimecommand.shimeCommand
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory

/**
 * アノテーション方式のサンプルコマンド。
 *
 * ルートは [ShimeCommandDefinition]、サブコマンドは [ShimeCommandHandler] の `path` で表現する。
 * ハンドラーには [ShimeCommandContext] と [ShimeCommandArg] 付きの位置引数を注入できる。
 */
@ShimeCommandDefinition(
    name = "shimesampleadmin",
    permission = "shimesample.admin",
    description = "アノテーション方式のサンプルコマンド。"
)
class ShimeSampleAnnotatedCommands {
    /** ルートコマンド。 */
    @ShimeCommandHandler
    fun root(context: ShimeCommandContext): ShimeCommandResult {
        context.sender.sendMessage("Hello from the annotated command!")
        return ShimeCommandResult.SilentSuccess
    }

    /** 入力された文字列をそのまま返すサブコマンド。 */
    @ShimeCommandHandler(path = ["echo"])
    fun echo(
        context: ShimeCommandContext,
        @ShimeCommandArg(name = "message", type = ShimeCommandArgumentType.STRING) message: String
    ): ShimeCommandResult {
        context.sender.sendMessage(message)
        return ShimeCommandResult.SilentSuccess
    }
}

/**
 * ShimePluginLibrary を利用する最小構成のサンプルプラグイン。
 *
 * ライブラリの初期化は ShimePluginLibrary 本体が行うため、ここでは `ShimeLibrary.getOrNull()` で
 * 初期化済みインスタンスを取得し、コマンド API だけを登録する。
 */
class ShimeSamplePlugin : JavaPlugin() {
    private var registration: ShimeCommandRegistration? = null
    private var annotatedRegistration: ShimeCommandRegistration? = null

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
            annotatedRegistration = commands.registerAnnotated(ShimeSampleAnnotatedCommands(), dynamic = true)
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
        annotatedRegistration?.unregister()
        annotatedRegistration = null
        registration?.unregister()
        registration = null
    }
}
