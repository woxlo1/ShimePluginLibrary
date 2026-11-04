package shimeserver.shimepluginlibrary.shimecommand

import shimeserver.shimepluginlibrary.shimeexception.ShimeException

/** コマンドの登録および解析の失敗に関する基底例外。 */
open class ShimeCommandException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/**
 * 引数パーサーが、その引数に対してユーザー入力が不正であると判断したときにスローされる。
 *
 * [argumentIndex] は 0 始まりの位置、[argumentName] は宣言名、[input] は問題のある入力である。
 * パーサー自身が位置を知らない場合は null のままでよく、マネージャーが解析時に補完する。
 */
class ShimeCommandArgumentException(
    message: String,
    cause: Throwable? = null,
    val argumentIndex: Int? = null,
    val argumentName: String? = null,
    val input: String? = null
) : ShimeCommandException(message, cause)
