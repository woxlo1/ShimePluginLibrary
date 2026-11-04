package shimeserver.shimepluginlibrary.shimecommand

import java.util.Locale

/**
 * コマンドツリー内の1つのノード。
 *
 * ノードは通常 [shimeCommand] または [ShimeCommandDsl] を通して生成される。公開 API は読み取り専用であり、
 * 変更はこのモジュール内のビルダーに限定されているため、コマンドの実行中に登録済みのツリーが
 * 変更されることはない。
 */
class ShimeCommandNode internal constructor(
    name: String,
    val parent: ShimeCommandNode? = null
) {
    val name: String = name.lowercase(Locale.ROOT)
    internal val aliases = LinkedHashSet<String>()
    internal var permission: String? = null
    internal var senderType: ShimeCommandSenderType = ShimeCommandSenderType.ANY
    internal var cooldownMillis: Long = 0L
    internal var executionLimit: Int = 0
    internal var executionWindowMillis: Long = 0L
    internal var usage: String? = null
    internal var description: String = ""
    internal var executor: ShimeCommandExecutor? = null
    internal var tabCompleter: ShimeCommandTabCompleter? = null
    internal val arguments = ArrayList<ShimeCommandArgumentSpec>()
    internal val commandHooks = ArrayList<ShimeCommandHook>()
    private val childNodes = LinkedHashMap<String, ShimeCommandNode>()

    init {
        require(this.name.matches(Regex("[a-z0-9_-]+"))) {
            "Command name '$name' may only contain lowercase letters, digits, underscores and hyphens."
        }
    }

    /** ルートからの完全なパス。例: `admin reload`。 */
    val path: String
        get() = parent?.let { "${it.path} $name" } ?: name

    /** 使用方法やヘルプの行で既定のラベルとして使われるルートコマンド名。 */
    private val rootName: String
        get() = parent?.rootName ?: name

    /** ルートコマンドのラベルを基準とした相対パス。ルートノードでは空になる。 */
    val relativePath: String
        get() = if (parent == null) "" else listOfNotNull(parent.relativePath.takeIf { it.isNotBlank() }, name)
            .joinToString(" ")

    /** 直接の子ノードの不変ビュー。 */
    val children: List<ShimeCommandNode>
        get() = childNodes.values.toList()

    /** このノードに登録された実行フックの不変ビュー。 */
    val hooks: List<ShimeCommandHook>
        get() = commandHooks.toList()

    /** [token] に名前またはエイリアスで一致する子ノードを返す。 */
    fun child(token: String): ShimeCommandNode? {
        val normalized = token.lowercase(Locale.ROOT)
        return childNodes[normalized] ?: childNodes.values.firstOrNull { normalized in it.aliases }
    }

    /** `/example give <player> [amount]` のような使用方法の行を組み立てる。 */
    @JvmOverloads
    fun usageLine(label: String = rootName): String {
        val base = usage?.takeIf { it.isNotBlank() } ?: buildString {
            append('/').append(label)
            if (relativePath.isNotBlank()) {
                append(' ').append(relativePath)
            }
            arguments.forEach { spec ->
                append(' ')
                if (spec.optional) {
                    append('[').append(spec.name).append(']')
                } else {
                    append('<').append(spec.name).append('>')
                }
            }
        }
        return base
    }

    /** このノードとその直接の子ノードについての、人間が読めるヘルプ。 */
    @JvmOverloads
    fun helpLines(label: String = rootName): List<String> {
        val lines = ArrayList<String>()
        lines += usageLine(label)
        if (description.isNotBlank()) {
            lines += description
        }
        if (!permission.isNullOrBlank()) {
            lines += "Permission: $permission"
        }
        if (aliases.isNotEmpty()) {
            lines += "Aliases: ${aliases.joinToString()}"
        }
        if (children.isNotEmpty()) {
            lines += "Subcommands:"
            children.sortedBy { it.name }.forEach { child ->
                val suffix = child.description.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
                lines += "  ${child.name}$suffix"
            }
        }
        return lines
    }

    internal fun addChild(child: ShimeCommandNode) {
        val tokens = setOf(child.name) + child.aliases
        val conflict = tokens.firstOrNull { token ->
            childNodes.containsKey(token) || childNodes.values.any { token == it.name || token in it.aliases }
        }
        require(conflict == null) {
            "Command '${path}' already has a subcommand or alias named '$conflict'."
        }
        childNodes[child.name] = child
    }
}
