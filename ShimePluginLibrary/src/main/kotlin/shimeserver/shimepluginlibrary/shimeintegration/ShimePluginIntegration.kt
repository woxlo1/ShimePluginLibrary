package shimeserver.shimepluginlibrary.shimeintegration

import org.bukkit.Bukkit
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext

/**
 * Bukkit のプラグインマネージャーから利用可否を報告する基底インテグレーション。
 *
 * プラグイン名は大文字小文字を区別せずに比較される。サブクラスは [onEnable] と [onDisable] を
 * オーバーライドして対象プラグインの API をブリッジする。このクラスはサードパーティのプラグイン
 * クラスに意図的に依存しない。
 */
open class ShimePluginIntegration(
    final override val id: String,
    private val pluginName: String
) : ShimeIntegration {
    init {
        require(id.isNotBlank()) { "Integration id must not be blank." }
        require(pluginName.isNotBlank()) { "Integration plugin name must not be blank." }
    }

    override fun isAvailable(): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin(pluginName) ?: return false
        return plugin.isEnabled
    }

    final override fun enable(context: ShimeContext) {
        onEnable(context)
    }

    final override fun disable() {
        onDisable()
    }

    /** 利用可否が確認された後に呼び出される。 */
    protected open fun onEnable(context: ShimeContext) {
        // サブクラスはここで対象 API をブリッジする。
    }

    /** シャットダウン時に呼び出される。 */
    protected open fun onDisable() {
        // サブクラスはここでブリッジのリソースを解放する。
    }
}
