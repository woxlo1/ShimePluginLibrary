package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * 利用側プラグインの無効化を監視し、対応する [ShimePluginScope] を解放するリスナー。
 *
 * このリスナーはライブラリ本体のプラグインに登録されるため、利用側プラグインのリスナーが
 * 解除された後でも `PluginDisableEvent` を受け取れる。
 */
internal class ShimeScopeDisableListener(private val library: ShimeLibrary) : Listener {
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        val disabled = event.plugin
        if (disabled is JavaPlugin) {
            library.releaseScope(disabled)
        }
    }
}
