package shimeserver.shimepluginlibrary.shimecommand

/**
 * コマンドエグゼキューターが返す統一された結果。
 *
 * 例外をスローする代わりに結果を返すことで、想定内の失敗（不正な入力、権限不足、使用方法の誤り）を、
 * マネージャーがスタックトレース付きでログ出力する想定外の例外と分けて扱える。
 */
sealed class ShimeCommandResult {
    /** コマンドが完了した。任意のメッセージが送信者に送られる。 */
    data class Success(val message: String? = null) : ShimeCommandResult()

    /** コマンドが、送信者に見せても安全な理由で失敗した。 */
    data class Failure(val message: String, val cause: Throwable? = null) : ShimeCommandResult()

    /** 送信者が不正な構文を使用した。マネージャーがコマンドの使用方法の行を付加する。 */
    data class Usage(val message: String? = null) : ShimeCommandResult()

    /** 送信者に権限ノードが不足している。 */
    data class NoPermission(val permission: String? = null) : ShimeCommandResult()

    /** コマンドが何も送信せずに完了した。 */
    object SilentSuccess : ShimeCommandResult()

    val isSuccess: Boolean
        get() = this is Success || this === SilentSuccess
}
