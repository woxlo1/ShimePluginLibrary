package shimeserver.shimepluginlibrary.shimecommand

/** コマンドノードに対する送信元の制限。 */
enum class ShimeCommandSenderType {
    ANY,
    PLAYER,
    CONSOLE;

    fun allows(isPlayer: Boolean): Boolean {
        return when (this) {
            ANY -> true
            PLAYER -> isPlayer
            CONSOLE -> !isPlayer
        }
    }
}
