package shimeserver.shimepluginlibrary.shimenms

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimepacket.ShimePacketManager
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.io.File

/**
 * 初期化時に NMS アダプターへ渡される不変のサービス群。
 *
 * アダプターは検出されたバージョンと共有のリフレクションヘルパーを受け取る。アダプターは自身の
 * shutdown 以降もプラグインへの参照を保持してはならず、割り当てたすべてのリソースを解放しなければならない。
 */
class ShimeNmsAdapterContext(
    val plugin: JavaPlugin,
    val logger: ShimeLogger,
    val version: ShimeNmsVersion,
    val reflection: ShimeReflection,
    val packets: ShimePacketManager
) {
    /** バージョン固有のデータをキャッシュする必要があるアダプター向けに提供されるプラグインデータフォルダー。 */
    val dataFolder: File
        get() = plugin.dataFolder
}
