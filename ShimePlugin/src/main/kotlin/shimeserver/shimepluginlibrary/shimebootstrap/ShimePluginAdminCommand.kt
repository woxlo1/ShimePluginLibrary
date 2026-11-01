package shimeserver.shimepluginlibrary.shimebootstrap

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeLibrary
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandManager
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandRegistration
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandResult
import shimeserver.shimepluginlibrary.shimecommand.shimeCommand
import shimeserver.shimepluginlibrary.shimenms.ShimeNmsManager

/**
 * ライブラリの管理コマンドを登録する。
 *
 * コマンドは `plugin.yml` で宣言されており、マネージャーが宣言された [org.bukkit.command.PluginCommand] を通じてバインドする。
 * 技術的な出力には、意図的にパスワードや接続文字列を含めていない。
 */
object ShimePluginAdminCommand {
    /** 管理コマンドの使用に必要な権限。 */
    const val PERMISSION = "shimepluginlibrary.admin"

    /**
     * コマンドを構築して登録する。
     *
     * @throws shimeserver.shimepluginlibrary.shimecommand.ShimeCommandException コマンドをバインドできない場合。
     */
    @JvmStatic
    fun register(
        plugin: JavaPlugin,
        library: ShimeLibrary,
        commands: ShimeCommandManager,
        nms: ShimeNmsManager
    ): ShimeCommandRegistration {
        val root = shimeCommand("shimepluginlibrary") {
            alias("spl", "shimelib")
            permission(PERMISSION)
            description("ShimePluginLibrary administration command.")
            executes { context, _ ->
                context.manager.sendHelp(context.sender, context.root, context.label)
                ShimeCommandResult.SilentSuccess
            }
            subcommand("status") {
                description("Shows library, database and NMS status.")
                executes { context, _ -> status(context.sender, plugin, library, commands, nms) }
            }
            subcommand("reload") {
                description("Reloads configuration and language files.")
                executes { context, _ -> reload(context.sender, library) }
            }
            subcommand("nms") {
                description("Shows the NMS capability report.")
                executes { context, _ -> nmsReport(context.sender, library, nms) }
            }
            subcommand("services") {
                description("Lists registered services and their states.")
                executes { context, _ -> services(context.sender, library) }
            }
        }
        return commands.register(root)
    }

    private fun status(
        sender: CommandSender,
        plugin: JavaPlugin,
        library: ShimeLibrary,
        commands: ShimeCommandManager,
        nms: ShimeNmsManager
    ): ShimeCommandResult {
        val lines = ArrayList<String>()
        lines += "ShimePluginLibrary ${plugin.pluginMeta.version} - state ${library.state}"
        lines += "Services: " + library.serviceStates().entries.joinToString { "${it.key}=${it.value}" }
        lines += "Databases: " + library.databaseManager.names().joinToString().ifBlank { "none" }
        lines += "Language files: " + library.messageManager.locales().joinToString().ifBlank { "none" }
        lines += "Commands: " + commands.registeredNames().joinToString().ifBlank { "none" }
        lines += "NMS: " + (nms.versionOrNull()?.minecraftVersion ?: "unknown") +
            " / " + (nms.adapterStatus() ?: "unavailable")
        lines.forEach(sender::sendMessage)
        return ShimeCommandResult.SilentSuccess
    }

    private fun reload(sender: CommandSender, library: ShimeLibrary): ShimeCommandResult {
        return try {
            library.configManager.reloadAll()
            library.configManager.get("config")?.let { config ->
                library.logger.setDebugEnabled(config.getBoolean("debug", false))
                library.messageManager.configure(
                    config.getString("language", "en_US") ?: "en_US",
                    config.getBoolean("use-prefix", true)
                )
            }
            library.messageManager.reload()
            val locale = localeOf(sender, library)
            sender.sendMessage(library.messageManager.component(locale, "command.reload.success"))
            ShimeCommandResult.SilentSuccess
        } catch (throwable: Throwable) {
            library.logger.report(shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory.CONFIG, "Reload", throwable)
            ShimeCommandResult.Failure("Reload failed. Check the server log for details.", throwable)
        }
    }

    private fun nmsReport(sender: CommandSender, library: ShimeLibrary, nms: ShimeNmsManager): ShimeCommandResult {
        return try {
            nms.report().summary().lineSequence().forEach(sender::sendMessage)
            ShimeCommandResult.SilentSuccess
        } catch (throwable: Throwable) {
            library.logger.report(shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory.NMS, "NMS report", throwable)
            ShimeCommandResult.Failure("The NMS report is unavailable. Check the server log for details.", throwable)
        }
    }

    private fun services(sender: CommandSender, library: ShimeLibrary): ShimeCommandResult {
        val states = library.serviceStates()
        if (states.isEmpty()) {
            sender.sendMessage("No services are registered.")
            return ShimeCommandResult.SilentSuccess
        }
        states.toSortedMap().forEach { (name, state) ->
            sender.sendMessage("$name: $state")
        }
        return ShimeCommandResult.SilentSuccess
    }

    private fun localeOf(sender: CommandSender, library: ShimeLibrary): String {
        return if (sender is Player) sender.locale().toString() else library.messageManager.defaultLocale()
    }
}
