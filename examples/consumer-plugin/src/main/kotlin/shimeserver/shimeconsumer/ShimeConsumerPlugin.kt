package shimeserver.shimeconsumer

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimecommand.ShimeCommandResult
import shimeserver.shimepluginlibrary.shimenms.ShimeMinecraftVersion
import shimeserver.shimepluginlibrary.shimenms.ShimeNmsFeature
import shimeserver.shimepluginlibrary.shimeutil.ShimeText

/**
 * 公開された 3 つの成果物を解決してコンパイルするための利用者サンプル。
 *
 * このクラス自体は機能を提供しない。`ShimePluginLibrary`、`ShimePluginCommand`、
 * `ShimePluginNMS` の公開 API を参照することで、Maven 座標と推移的依存関係が解決できることを
 * ビルド時に検証する。
 */
class ShimeConsumerPlugin : JavaPlugin() {
    override fun onEnable() {
        val version = ShimeMinecraftVersion.PAPER_1_21_8
        val feature = ShimeNmsFeature.VERSION_DETECTION
        val text = ShimeText.plain("<gray>ShimeConsumerPlugin</gray>")
        val result: ShimeCommandResult = ShimeCommandResult.SilentSuccess
        logger.info("Published artifacts resolved: $version, $feature, $text, success=${result.isSuccess}")
    }
}
