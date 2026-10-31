package shimeserver.shimepluginlibrary

import org.bukkit.plugin.java.JavaPlugin

/** ShimePluginLibraryをサーバーへ登録するプラグインエントリーポイントです。 */
class ShimePlugin : JavaPlugin() {
    override fun onEnable() {
        logger.info("ShimePluginLibrary is starting.")
        // 各モジュールの初期化処理は、実装フェーズで専用クラスへ委譲します。
    }

    override fun onDisable() {
        // 各モジュールのリソース解放処理を実装フェーズで追加します。
    }
}
