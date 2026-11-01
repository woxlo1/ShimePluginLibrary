package shimeserver.shimepluginlibrary.shimecommand

/** コマンドノード上に位置引数を1つ宣言する。 */
class ShimeCommandArgumentSpec(
    val name: String,
    val argument: ShimeArgument<*>,
    val optional: Boolean = false,
    val defaultValue: Any? = null
) {
    init {
        require(name.isNotBlank()) { "Argument name must not be blank." }
        require(name.matches(Regex("[A-Za-z0-9_-]+"))) {
            "Argument name '$name' may only contain letters, digits, underscores and hyphens."
        }
    }
}
