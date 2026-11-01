package shimeserver.shimepluginlibrary.shimeexception

/** ライブラリ固有のすべての失敗に対する基底例外。 */
open class ShimeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 無効な状態でライフサイクル操作が要求された場合にスローされる。 */
class ShimeLifecycleException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** サービスの登録、検索、初期化ができない場合にスローされる。 */
class ShimeServiceException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** ユーザー入力または設定入力の検証に失敗した場合にスローされる。 */
class ShimeValidationException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** 設定ファイルが存在しない、形式が不正、または無効な値を含む場合にスローされる。 */
class ShimeConfigurationException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** ストレージバックエンドが操作を完了できない場合にスローされる。 */
class ShimeStorageException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** データベース操作が失敗した場合にスローされる。 */
class ShimeDatabaseException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** 呼び出し元がアクセスを必要とする状況で権限チェックが失敗した場合にスローされる。 */
class ShimePermissionException(message: String, cause: Throwable? = null) : ShimeException(message, cause)

/** 実行中のサーバーバージョンで機能が利用できない場合にスローされる。 */
class ShimeUnsupportedVersionException(message: String, cause: Throwable? = null) : ShimeException(message, cause)
