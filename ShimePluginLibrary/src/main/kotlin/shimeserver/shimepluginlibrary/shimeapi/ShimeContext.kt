package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import java.io.File

/**
 * 初期化時にサービスへ渡される不変のアクセスポイント。
 *
 * これらの参照を 1 つのオブジェクトにまとめることで、サービス内での隠れたグローバル参照を避け、
 * 制御されたコンテキストでサービスを初期化できるためテストも容易になる。
 */
class ShimeContext(
    val plugin: JavaPlugin,
    val library: ShimeLibrary,
    val logger: ShimeLogger,
    val scheduler: ShimeScheduler,
    val dataFolder: File
)
