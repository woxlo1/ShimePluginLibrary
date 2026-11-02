package shimeserver.shimepluginlibrary.shimecommand

/**
 * コマンド実行の前後に呼ばれるフック。
 *
 * フックは登録順に呼ばれ、[beforeExecution] が false を返すとエグゼキューターは実行されません。
 * [afterExecution] はエグゼキューターが実行された場合にのみ呼ばれ、登録とは逆順に実行されます。
 * フックの例外はログに記録され、他のフックやコマンドの結果には影響しません。
 */
interface ShimeCommandHook {
    /** 実行を許可する場合は true、中止する場合は false を返す。 */
    fun beforeExecution(context: ShimeCommandContext): Boolean = true

    /** 実行結果を受け取る。 */
    fun afterExecution(context: ShimeCommandContext, result: ShimeCommandResult) {}
}
