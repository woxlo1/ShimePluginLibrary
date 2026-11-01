package shimeserver.shimepluginlibrary.shimeservice

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeapi.ShimeLifecycle
import shimeserver.shimepluginlibrary.shimeapi.ShimeState
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger

/**
 * 正当なライフサイクル遷移を強制し、初期化の失敗がレジストリから確実に
 * 認識されるようにする基底実装。
 */
abstract class ShimeAbstractService(
    override val serviceName: String,
    override val servicePriority: Int = 0,
    override val serviceDependencies: Set<String> = emptySet()
) : ShimeService {
    private val lifecycle = ShimeLifecycle()

    override val state: ShimeState
        get() = lifecycle.state

    final override fun initialize(context: ShimeContext) {
        lifecycle.transition(
            serviceName,
            "initialize",
            setOf(ShimeState.UNINITIALIZED, ShimeState.FAILED),
            ShimeState.INITIALIZING
        )
        try {
            onInitialize(context)
            lifecycle.transition(
                serviceName,
                "finish initialization",
                setOf(ShimeState.INITIALIZING),
                ShimeState.READY
            )
        } catch (throwable: Throwable) {
            lifecycle.force(ShimeState.FAILED)
            throw throwable
        }
    }

    final override fun shutdown() {
        val current = lifecycle.state
        if (current == ShimeState.STOPPED) {
            return
        }
        lifecycle.transition(
            serviceName,
            "shutdown",
            setOf(ShimeState.READY, ShimeState.FAILED, ShimeState.INITIALIZING),
            ShimeState.STOPPING
        )
        try {
            onShutdown()
        } finally {
            lifecycle.force(ShimeState.STOPPED)
        }
    }

    /** サービス固有の初期化を実行する。 */
    protected abstract fun onInitialize(context: ShimeContext)

    /** サービス固有のクリーンアップを実行する。実装は冪等でなければならない。 */
    protected open fun onShutdown() {
        // ほとんどのサービスには解放すべきリソースがない。
    }

    protected fun requireReady(operation: String) {
        lifecycle.requireReady(serviceName, operation)
    }

    protected fun logInfo(logger: ShimeLogger, message: String) {
        logger.info(ShimeLogCategory.SERVICE, "[$serviceName] $message")
    }

    protected fun logWarn(logger: ShimeLogger, message: String, throwable: Throwable? = null) {
        logger.warn(ShimeLogCategory.SERVICE, "[$serviceName] $message", throwable)
    }
}
