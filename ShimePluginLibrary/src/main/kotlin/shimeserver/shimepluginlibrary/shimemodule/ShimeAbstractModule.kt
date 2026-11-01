package shimeserver.shimepluginlibrary.shimemodule

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeapi.ShimeLifecycle
import shimeserver.shimepluginlibrary.shimeapi.ShimeState

/**
 * 正しいライフサイクル遷移を強制する基底モジュール。
 *
 * 実装側は [onInitialize] と [onShutdown] をオーバーライドする。初期化中に失敗するとモジュールは
 * 失敗状態となり、マネージャーが一貫した状態を報告できる。
 */
abstract class ShimeAbstractModule(
    override val moduleId: String,
    override val modulePriority: Int = 0,
    override val moduleDependencies: Set<String> = emptySet()
) : ShimeModule {
    private val lifecycle = ShimeLifecycle()

    override val state: ShimeState
        get() = lifecycle.state

    final override fun initialize(context: ShimeContext) {
        lifecycle.transition(
            moduleId,
            "initialize",
            setOf(ShimeState.UNINITIALIZED, ShimeState.FAILED),
            ShimeState.INITIALIZING
        )
        try {
            onInitialize(context)
            lifecycle.transition(moduleId, "finish initialization", setOf(ShimeState.INITIALIZING), ShimeState.READY)
        } catch (throwable: Throwable) {
            lifecycle.force(ShimeState.FAILED)
            throw throwable
        }
    }

    final override fun shutdown() {
        if (lifecycle.state == ShimeState.STOPPED) {
            return
        }
        lifecycle.transition(
            moduleId,
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

    /** モジュール固有の初期化を実行する。 */
    protected abstract fun onInitialize(context: ShimeContext)

    /** モジュール固有の後片付けを実行する。実装は冪等でなければならない。 */
    protected open fun onShutdown() {
        // ほとんどのモジュールは解放すべきリソースを持たない。
    }
}
