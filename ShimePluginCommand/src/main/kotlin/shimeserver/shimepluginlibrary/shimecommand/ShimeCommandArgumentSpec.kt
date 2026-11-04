package shimeserver.shimepluginlibrary.shimecommand

/** 既定値を実行時に解決するプロバイダー。 */
fun interface ShimeCommandDefaultProvider {
    /** 実際の送信者に依存する既定値を返す。 */
    fun resolve(context: ShimeCommandContext): Any?
}

/**
 * コマンドノード上に位置引数を1つ宣言する。
 *
 * [defaultProvider] は [defaultValue] より優先され、省略時は送信者に依存する既定値を解決できる。
 * [completionPermission] が設定されている場合、その権限を持たない送信者には補完候補を返さない。
 */
class ShimeCommandArgumentSpec(
    val name: String,
    val argument: ShimeArgument<*>,
    val optional: Boolean = false,
    val defaultValue: Any? = null,
    val defaultProvider: ShimeCommandDefaultProvider? = null,
    val completionPermission: String? = null
) {
    init {
        require(name.isNotBlank()) { "Argument name must not be blank." }
        require(name.matches(Regex("[A-Za-z0-9_-]+"))) {
            "Argument name '$name' may only contain letters, digits, underscores and hyphens."
        }
        require(completionPermission == null || completionPermission.isNotBlank()) {
            "Completion permission must not be blank when specified."
        }
    }
}
