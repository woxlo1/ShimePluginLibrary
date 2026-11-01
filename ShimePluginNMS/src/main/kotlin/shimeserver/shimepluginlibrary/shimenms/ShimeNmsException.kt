package shimeserver.shimepluginlibrary.shimenms

import shimeserver.shimepluginlibrary.shimeexception.ShimeException

/** NMS の検出、アダプター、リフレクションの失敗に対する基底例外。 */
open class ShimeNmsException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** 要求された NMS 機能が稼働中のサーバーまたはアダプターで利用できない場合に投げられる。 */
class ShimeNmsUnsupportedException(message: String, cause: Throwable? = null) : ShimeNmsException(message, cause)

/** メンバーの検出後にリフレクションによる検索や呼び出しが失敗した場合に投げられる。 */
class ShimeNmsReflectionException(message: String, cause: Throwable? = null) : ShimeNmsException(message, cause)
