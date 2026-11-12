package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
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

    private val closed = AtomicBoolean(false)

    /** このスコープが解放済みかどうか。解放後のスコープは再利用できない。 */
    val isClosed: Boolean
        get() = closed.get()

    /**
     * このスコープが所有するリソースを解放する。
     *
     * このメソッドは冪等であり、複数回呼び出しても安全である。解放に成功したスコープは
     * ライブラリのキャッシュから取り除かれるため、再度 [ShimeLibrary.forPlugin] を呼ぶと
     * 新しいインスタンスが返る。
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            scheduler.cancelAll()
        } finally {
            library.forgetScope(this)
        }
    }
}
