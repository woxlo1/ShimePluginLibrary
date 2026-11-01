package shimeserver.shimepluginlibrary

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimebootstrap.ShimePluginBootstrap
import java.util.logging.Level

/**
 * ShimePluginLibrary ディストリビューションの Paper 用エントリーポイント。
 *
 * ライフサイクル処理は [ShimePluginBootstrap] に委譲される。このクラスを小さく保つことで、
 * 起動に失敗した場合でも、部分的に初期化されたサービスがエントリーポイント内に隠れたままになることなく報告できる。
 */
class ShimePlugin : JavaPlugin() {
    private var bootstrap: ShimePluginBootstrap? = null

    override fun onEnable() {
        val startup = ShimePluginBootstrap(this)
        bootstrap = startup
        try {
            startup.enable()
        } catch (throwable: Throwable) {
            logger.log(Level.SEVERE, "ShimePluginLibrary could not be enabled.", throwable)
            try {
                startup.disable()
            } catch (cleanupFailure: Throwable) {
                logger.log(Level.SEVERE, "Cleanup after a failed startup also failed.", cleanupFailure)
            }
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        val current = bootstrap
        bootstrap = null
        if (current == null) {
            return
        }
        try {
            current.disable()
        } catch (throwable: Throwable) {
            logger.log(Level.SEVERE, "ShimePluginLibrary could not be disabled cleanly.", throwable)
        }
    }
}
