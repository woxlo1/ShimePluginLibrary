package shimeserver.shimepluginlibrary.shimecommand

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler

/**
 * コマンド1回の呼び出しに対する実行コンテキスト。
 *
 * コンテキストは不変である。[args] にはサブコマンド解決後の残りのトークンが入り、
 * [rawArgs] は送信者が入力した内容をそのまま保持する。[arguments] は解析済みの位置引数であり、
 * 実行フックやエグゼキューターから名前で参照できる。スケジューラーを公開しているため、
 * エグゼキューターはグローバル状態を捕捉することなく、負荷の高い処理をプライマリスレッドの外へ移せる。
 */
class ShimeCommandContext(
    val sender: CommandSender,
    val root: ShimeCommandNode,
    val node: ShimeCommandNode,
    val label: String,
    val args: List<String>,
    val rawArgs: List<String>,
    val manager: ShimeCommandManager,
    val plugin: JavaPlugin,
    val scheduler: ShimeScheduler,
    val arguments: Map<String, Any?> = emptyMap()
) {
    val isPlayer: Boolean
        get() = sender is Player

    val isConsole: Boolean
        get() = sender !is Player

    /** 送信者をプレイヤーとして返す。プレイヤーでない場合はユーザー向けの引数エラーをスローする。 */
    fun requirePlayer(): Player {
        return sender as? Player
            ?: throw ShimeCommandArgumentException("This command can only be used by a player.")
    }

    /** Bukkit の権限判定を使って [permission] を持っているかを返す。 */
    fun hasPermission(permission: String): Boolean {
        return permission.isBlank() || sender.hasPermission(permission)
    }

    /** 送信者にプレーンメッセージを送信する。 */
    fun reply(message: String) {
        sender.sendMessage(message)
    }

    /** 送信者にコンポーネントを送信する。 */
    fun reply(component: Component) {
        sender.sendMessage(component)
    }
}
