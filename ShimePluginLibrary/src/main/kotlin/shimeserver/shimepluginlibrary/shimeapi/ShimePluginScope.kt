package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeconfig.ShimeConfigManager
import shimeserver.shimepluginlibrary.shimeevent.ShimeEventManager
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimeservice.ShimeService
import shimeserver.shimepluginlibrary.shimestorage.ShimeStorageManager
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 利用側プラグインに紐づいたライブラリサービス群。
 *
 * [ShimeLibrary.forPlugin] から取得する。ロガー、スケジューラー、設定、ストレージなど、所有者が
 * 利用側プラグインであるべきサービスは、このスコープを通じてのみ利用する。データベースプールや
 * キャッシュのような重いサービスはスコープで複製せず、[ShimeLibrary] が持つ共有インスタンスを
 * 引き続き利用する。
 *
 * スコープは利用側プラグインの `PluginDisableEvent`、または [close] の呼び出しで解放される。
 * [ShimeLibrary.shutdown] は残っているすべてのスコープを解放する。解放後のスコープは再利用できず、
 * [isClosed] が true になる。
 */
class ShimePluginScope internal constructor(
    /** このスコープを所有する利用側プラグイン。 */
    val plugin: JavaPlugin,
    private val library: ShimeLibrary
) : AutoCloseable {
    /**
     * 利用側プラグインのロガー。出力の接頭辞は Bukkit が付与する利用側プラグイン名になる。
     */
    val logger: ShimeLogger = ShimeLogger(plugin.logger)

    /**
     * 利用側プラグインが所有するスケジューラー。
     *
     * このスケジューラーで作成したタスクは Bukkit 上でも利用側プラグインに紐づくため、利用側を
     * 無効化するとサーバーによって停止される。加えてスコープの解放時に [ShimeScheduler.cancelAll] を
     * 呼び、追跡中のタスクを明示的に取り消す。
     */
    val scheduler: ShimeScheduler = ShimeScheduler(plugin, logger)

    private val context: ShimeContext = ShimeContext(plugin, library, logger, scheduler, plugin.dataFolder)
    private val scopedServices = CopyOnWriteArrayList<ShimeService>()

    /**
     * 利用側プラグインのデータフォルダを使う設定マネージャー。
     *
     * [create] で作られる設定ファイルはライブラリのデータフォルダではなく、利用側プラグインの
     * データフォルダ直下に保存される。埋め込みリソースも利用側プラグインの JAR から読み込まれる。
     * スコープの解放時には登録済みの設定が保存される。
     *
     * ライブラリ共有の設定は従来どおり [ShimeLibrary.configManager] を利用できる。
     */
    val configManager: ShimeConfigManager = scoped(ShimeConfigManager(plugin, logger))

    /**
     * 利用側プラグインに紐づくイベントマネージャー。
     *
     * [ShimeEventManager.registerListener] と [ShimeEventManager.subscribe] は利用側プラグインを
     * 所有者として Paper に登録する。スコープの解放時には、このマネージャーが登録したリスナーと
     * 購読がすべて解除される。
     */
    val eventManager: ShimeEventManager = scoped(ShimeEventManager(plugin, logger))

    /**
     * 利用側プラグインの `storage/` 配下を使うストレージマネージャー。
     *
     * 新しいデータは利用側プラグインのデータフォルダに保存される。1.1.0 以前にライブラリの共有
     * フォルダへ保存されたデータは、同じ名前のストレージに限り読み取り専用のフォールバックとして
     * 参照される。スコープの解放時には、このマネージャーが開いたストレージが閉じられる。
     */
    val storageManager: ShimeStorageManager = scoped(
        ShimeStorageManager(plugin, logger, legacyStorageRoot())
    )

    private val closed = AtomicBoolean(false)

    /** このスコープが解放済みかどうか。解放後のスコープは再利用できない。 */
    val isClosed: Boolean
        get() = closed.get()

    /**
     * このスコープが所有するリソースを解放する。
     *
     * このメソッドは冪等であり、複数回呼び出しても安全である。スコープが所有するサービスは作成の
     * 逆順で停止し、追跡中のタスクは取り消される。解放に成功したスコープはライブラリのキャッシュから
     * 取り除かれるため、再度 [ShimeLibrary.forPlugin] を呼ぶと新しいインスタンスが返る。
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            scopedServices.asReversed().forEach { service ->
                try {
                    service.shutdown()
                } catch (throwable: Throwable) {
                    logger.warn(
                        ShimeLogCategory.CORE,
                        "Could not shut down scoped service '${service.serviceName}'.",
                        throwable
                    )
                }
            }
        } finally {
            try {
                scheduler.cancelAll()
            } finally {
                library.forgetScope(this)
            }
        }
    }

    /** [service] をこのスコープの管理下に置き、利用側プラグイン向けのコンテキストで初期化する。 */
    private fun <T : ShimeService> scoped(service: T): T {
        scopedServices += service
        service.initialize(context)
        return service
    }

    /** ライブラリの共有ストレージ。利用側と同一の場所ならフォールバックは不要。 */
    private fun legacyStorageRoot(): Path? {
        val legacy = library.context.dataFolder.toPath().resolve("storage")
        val current = plugin.dataFolder.toPath().resolve("storage")
        return legacy.takeIf { it != current }
    }
}
