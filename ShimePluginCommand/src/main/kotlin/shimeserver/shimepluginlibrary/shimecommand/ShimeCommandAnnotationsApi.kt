package shimeserver.shimepluginlibrary.shimecommand

/**
 * アノテーション方式のコマンド定義で、引数の解決方法を指定する。
 */
enum class ShimeCommandArgumentType {
    /** メソッド引数の型から自動判定する。 */
    AUTO,
    STRING,
    INTEGER,
    LONG,
    DECIMAL,
    BOOLEAN,
    UUID,
    PLAYER,
    OFFLINE_PLAYER,
    WORLD,
    /** メソッド引数の列挙型を使う。 */
    ENUM,
    /** [ShimeCommandArg.choices] のいずれか。 */
    CHOICE,
    /** [ShimeCommandAnnotations.registerArgumentType] で登録したパーサーを使う。 */
    CUSTOM
}

/**
 * アノテーション方式のルートコマンド定義。
 *
 * 対象クラスは1つだけこのアノテーションを持てる。サブコマンドは [ShimeCommandHandler.path] で表現する。
 *
 * @property name Bukkit に登録するコマンド名。小文字、数字、アンダースコア、ハイフンのみ。
 * @property aliases ルートコマンドのエイリアス。
 * @property permission ルートコマンドに必要な権限。空文字は権限なし。
 * @property description ヘルプに表示する説明。
 * @property usage 使用方法の行を上書きする場合に指定する。
 * @property senderType 実行可能な送信者種別。
 * @property cooldownMillis 送信元ごとのクールダウン。0以下はクールダウンなし。
 * @property executionLimit 実行回数制限。0以下は制限なし。
 * @property executionWindowMillis 実行回数制限の期間。0以下は制限なし。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ShimeCommandDefinition(
    val name: String,
    val aliases: Array<String> = [],
    val permission: String = "",
    val description: String = "",
    val usage: String = "",
    val senderType: ShimeCommandSenderType = ShimeCommandSenderType.ANY,
    val cooldownMillis: Long = 0L,
    val executionLimit: Int = 0,
    val executionWindowMillis: Long = 0L
)

/**
 * アノテーション方式のコマンドハンドラー。
 *
 * @property path ルートからのサブコマンドパス。空配列はルート自身を表す。
 * @property permission このノードに必要な権限。空文字はルートまたは親の設定を維持する。
 * @property description ヘルプに表示する説明。
 * @property usage 使用方法の行を上書きする場合に指定する。
 * @property senderType 実行可能な送信者種別。[ShimeCommandSenderType.ANY] は親の設定を維持する。
 * @property cooldownMillis クールダウン。負値は親の設定を維持する。
 * @property executionLimit 実行回数制限。負値は親の設定を維持する。
 * @property executionWindowMillis 実行回数制限の期間。負値は親の設定を維持する。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ShimeCommandHandler(
    val path: Array<String> = [],
    val permission: String = "",
    val description: String = "",
    val usage: String = "",
    val senderType: ShimeCommandSenderType = ShimeCommandSenderType.ANY,
    val cooldownMillis: Long = -1L,
    val executionLimit: Int = -1,
    val executionWindowMillis: Long = -1L
)

/**
 * アノテーション方式の位置引数。
 *
 * @property name 引数名。空文字の場合はメソッド引数名、取得できない場合は `arg1` のような名前を使う。
 * @property type 引数の解決方法。
 * @property optional 任意引数にするか。
 * @property hasDefault 既定値を指定するか。
 * @property defaultValue [hasDefault] が true のときに使う既定値の文字列表現。
 * @property min 数値引数の最小値。空文字は下限なし。
 * @property max 数値引数の最大値。空文字は上限なし。
 * @property choices [ShimeCommandArgumentType.CHOICE] の候補。
 * @property customType [ShimeCommandArgumentType.CUSTOM] で使う登録名。
 * @property completionPermission 補完候補を表示するために必要な権限。空文字は制限なし。
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ShimeCommandArg(
    val name: String = "",
    val type: ShimeCommandArgumentType = ShimeCommandArgumentType.AUTO,
    val optional: Boolean = false,
    val hasDefault: Boolean = false,
    val defaultValue: String = "",
    val min: String = "",
    val max: String = "",
    val choices: Array<String> = [],
    val customType: String = "",
    val completionPermission: String = ""
)
