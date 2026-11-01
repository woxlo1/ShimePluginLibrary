package shimeserver.shimepluginlibrary.shimecommand

import shimeserver.shimepluginlibrary.shimeexception.ShimeException

/** コマンドの登録および解析の失敗に関する基底例外。 */
open class ShimeCommandException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** 引数パーサーが、その引数に対してユーザー入力が不正であると判断したときにスローされる。 */
class ShimeCommandArgumentException(message: String, cause: Throwable? = null) :
    ShimeCommandException(message, cause)
