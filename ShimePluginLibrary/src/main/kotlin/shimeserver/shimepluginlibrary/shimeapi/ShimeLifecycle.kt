package shimeserver.shimepluginlibrary.shimeapi

import shimeserver.shimepluginlibrary.shimeexception.ShimeLifecycleException
import java.util.concurrent.atomic.AtomicReference

/**
 * サービスやモジュールが使用するスレッドセーフな状態ホルダー。
 *
 * 呼び出し元は状態を直接変更するのではなく [transition] を使用すべきである。そうすることで無効な遷移が
 * 早期に拒否され、生成されるエラーからどのコンポーネントのどの操作が失敗したかが分かる。
 */
class ShimeLifecycle(initialState: ShimeState = ShimeState.UNINITIALIZED) {
    private val current = AtomicReference(initialState)

    val state: ShimeState
        get() = current.get()

    fun isReady(): Boolean = state == ShimeState.READY

    fun requireReady(component: String, operation: String) {
        val currentState = state
        if (currentState != ShimeState.READY) {
            throw ShimeLifecycleException(
                "$component cannot perform '$operation' while it is $currentState. " +
                    "Initialize it first and make sure that it has not been shut down."
            )
        }
    }

    /**
     * 現在の状態が [allowedFrom] に含まれる場合に [next] へ遷移する。
     *
     * @throws ShimeLifecycleException 遷移が有効でない場合。
     */
    fun transition(component: String, operation: String, allowedFrom: Set<ShimeState>, next: ShimeState) {
        while (true) {
            val previous = current.get()
            if (previous !in allowedFrom) {
                throw ShimeLifecycleException(
                    "$component cannot perform '$operation' while it is $previous. " +
                        "Allowed source states: ${allowedFrom.joinToString()}."
                )
            }
            if (current.compareAndSet(previous, next)) {
                return
            }
        }
    }

    /** 状態を強制的に [next] にする。復旧処理と停止処理の経路に限定される。 */
    fun force(next: ShimeState) {
        current.set(next)
    }
}
