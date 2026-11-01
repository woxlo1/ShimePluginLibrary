package shimeserver.shimepluginlibrary.shimecommand

/** Java から扱いやすいコマンドエグゼキューター。 */
fun interface ShimeCommandExecutor {
    fun execute(context: ShimeCommandContext, arguments: Map<String, Any?>): ShimeCommandResult
}

/** Java から扱いやすい動的タブ補完器。 */
fun interface ShimeCommandTabCompleter {
    fun complete(context: ShimeCommandContext, partial: String): List<String>
}
