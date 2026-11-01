package shimeserver.shimepluginlibrary.shimecommand

import java.util.Locale
import java.util.function.Consumer

/**
 * [shimeCommand] および [ShimeCommandDsl] が使用する流れるようなビルダー。
 *
 * ビルダーはスレッドセーフではない。プラグインの起動時にコマンドを構築し、完成したノードを登録すること。
 */
class ShimeCommandBuilder internal constructor(internal val node: ShimeCommandNode) {
    /** このノードのエイリアスを追加する。 */
    fun alias(vararg values: String): ShimeCommandBuilder {
        values.forEach { value ->
            val normalized = value.lowercase(Locale.ROOT)
            require(normalized.matches(Regex("[a-z0-9_-]+"))) {
                "Command alias '$value' may only contain lowercase letters, digits, underscores and hyphens."
            }
            require(normalized != node.name) { "A command cannot use its own name as an alias." }
            node.aliases += normalized
        }
        return this
    }

    /** このノードに必要な権限を設定する。 */
    fun permission(value: String): ShimeCommandBuilder {
        require(value.isNotBlank()) { "Permission node must not be blank." }
        node.permission = value
        return this
    }

    /** このノードをプレイヤー、コンソール、または任意の送信元に制限する。 */
    fun sender(type: ShimeCommandSenderType): ShimeCommandBuilder {
        node.senderType = type
        return this
    }

    /** このノードをプレイヤーに制限する。 */
    fun playerOnly(): ShimeCommandBuilder = sender(ShimeCommandSenderType.PLAYER)

    /** このノードをコンソールに制限する。 */
    fun consoleOnly(): ShimeCommandBuilder = sender(ShimeCommandSenderType.CONSOLE)

    /** 送信元ごとのクールダウンをミリ秒単位で設定する。 */
    fun cooldown(millis: Long): ShimeCommandBuilder {
        require(millis >= 0L) { "Cooldown must not be negative." }
        node.cooldownMillis = millis
        return this
    }

    /** [windowMillis] の期間内における送信元ごとの実行回数を制限する。 */
    fun limit(count: Int, windowMillis: Long): ShimeCommandBuilder {
        require(count > 0) { "Execution limit must be positive." }
        require(windowMillis > 0L) { "Execution limit window must be positive." }
        node.executionLimit = count
        node.executionWindowMillis = windowMillis
        return this
    }

    /** 自動生成される使用方法の行を上書きする。 */
    fun usage(value: String): ShimeCommandBuilder {
        node.usage = value
        return this
    }

    /** ヘルプの説明を設定する。 */
    fun description(value: String): ShimeCommandBuilder {
        node.description = value
        return this
    }

    /** 位置引数を追加する。 */
    @JvmOverloads
    fun argument(
        name: String,
        argument: ShimeArgument<*> = ShimeArgumentTypes.string(),
        optional: Boolean = false,
        defaultValue: Any? = null
    ): ShimeCommandBuilder {
        node.arguments += ShimeCommandArgumentSpec(name, argument, optional, defaultValue)
        return this
    }

    /** エグゼキューターを設定する。 */
    fun executor(executor: ShimeCommandExecutor): ShimeCommandBuilder {
        node.executor = executor
        return this
    }

    /** Kotlin のラムダでエグゼキューターを設定する。 */
    fun executes(block: (ShimeCommandContext, Map<String, Any?>) -> ShimeCommandResult): ShimeCommandBuilder {
        return executor(ShimeCommandExecutor { context, arguments -> block(context, arguments) })
    }

    /** 動的なタブ補完器を設定する。 */
    fun tabCompleter(completer: ShimeCommandTabCompleter): ShimeCommandBuilder {
        node.tabCompleter = completer
        return this
    }

    /** Kotlin のラムダで動的なタブ補完器を設定する。 */
    fun tab(block: (ShimeCommandContext, String) -> List<String>): ShimeCommandBuilder {
        return tabCompleter(ShimeCommandTabCompleter { context, partial -> block(context, partial) })
    }

    /** ネストしたサブコマンドを追加する。 */
    fun subcommand(name: String, block: ShimeCommandBuilder.() -> Unit): ShimeCommandBuilder {
        val child = ShimeCommandNode(name, node)
        node.addChild(child)
        ShimeCommandBuilder(child).block()
        return this
    }

    /** 完成したノードを返す。 */
    fun build(): ShimeCommandNode = node
}

/** Kotlin DSL でコマンドツリーを生成する。 */
fun shimeCommand(name: String, block: ShimeCommandBuilder.() -> Unit): ShimeCommandNode {
    val root = ShimeCommandNode(name)
    ShimeCommandBuilder(root).block()
    return root
}

/** コマンド DSL の Java から扱いやすいエントリーポイント。 */
object ShimeCommandDsl {
    /** [name] のビルダーを生成する。 */
    @JvmStatic
    fun builder(name: String): ShimeCommandBuilder = ShimeCommandBuilder(ShimeCommandNode(name))

    /** Java のラムダまたは匿名クラスを使ってコマンドツリーを生成する。 */
    @JvmStatic
    fun command(name: String, configure: Consumer<ShimeCommandBuilder>): ShimeCommandNode {
        val builder = builder(name)
        configure.accept(builder)
        return builder.build()
    }
}
