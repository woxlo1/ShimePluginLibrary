package shimeserver.shimepluginlibrary.shimelogging

import shimeserver.shimepluginlibrary.shimeexception.ShimeException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

/**
 * プラグインロガーを包む薄いロギングファサード。
 *
 * デバッグメッセージはオプトインである。エラーは throwable が渡された場合に常にそれを含めるが、
 * パスワード、トークン、接続文字列をメッセージに含めない責任は呼び出し元にある。
 */
class ShimeLogger(
    private val delegate: Logger,
    private val debugEnabled: Boolean = false
) {
    private val debugFlag = AtomicBoolean(debugEnabled)

    val isDebugEnabled: Boolean
        get() = debugFlag.get()

    fun setDebugEnabled(enabled: Boolean) {
        debugFlag.set(enabled)
    }

    fun info(category: ShimeLogCategory, message: String) {
        delegate.info("[${category.label}] $message")
    }

    fun warn(category: ShimeLogCategory, message: String, throwable: Throwable? = null) {
        delegate.log(Level.WARNING, "[${category.label}] $message", throwable)
    }

    fun error(category: ShimeLogCategory, message: String, throwable: Throwable? = null) {
        delegate.log(Level.SEVERE, "[${category.label}] $message", throwable)
    }

    fun debug(category: ShimeLogCategory, message: String) {
        if (debugFlag.get()) {
            delegate.info("[${category.label}/debug] $message")
        }
    }

    fun debug(category: ShimeLogCategory, message: String, throwable: Throwable?) {
        if (debugFlag.get()) {
            delegate.log(Level.INFO, "[${category.label}/debug] $message", throwable)
        }
    }

    /**
     * 予期しない失敗を、イベントやコマンドのディスパッチループを壊すことなく報告する。
     * 例外が黙って握りつぶされることはない。
     */
    fun report(category: ShimeLogCategory, operation: String, throwable: Throwable) {
        if (throwable is ShimeException && throwable.cause == null) {
            warn(category, "$operation failed: ${throwable.message}")
        } else {
            error(category, "$operation failed.", throwable)
        }
    }
}
