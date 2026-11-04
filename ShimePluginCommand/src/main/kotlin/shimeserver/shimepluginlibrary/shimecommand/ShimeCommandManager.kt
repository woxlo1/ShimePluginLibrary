package shimeserver.shimepluginlibrary.shimecommand

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * コマンドツリーを Paper に登録し、実行、権限、クールダウン、補完を振り分ける。
 *
 * `plugin.yml` で宣言されたコマンドは [PluginCommand] を通じてバインドされる。[register] が
 * `dynamic = true` で呼ばれた場合、コマンドは実行時にサーバーのコマンドマップにも追加される。
 * これはすべてのコマンドを静的に列挙できないプラグインに有用である。動的コマンドはシャットダウン時に
 * 登録解除される。
 *
 * ルートコマンドが認識された時点で振り分けは常に true を返すため、検証に失敗しても Paper が独自の
 * 使用方法メッセージを出力することはない。
 */
class ShimeCommandManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger,
    private val scheduler: ShimeScheduler,
    val auditLog: ShimeCommandAuditLog = ShimeCommandAuditLog()
) : ShimeAbstractService(SERVICE_NAME) {
    private data class Binding(
        val root: ShimeCommandNode,
        val commandName: String,
        val declared: PluginCommand?,
        val dynamicCommand: ShimeBukkitCommand?
    )

    private data class UsageCounter(var windowStart: Long, var count: Int)

    private class MutableExecutionStats {
        private var executions = 0L
        private var successes = 0L
        private var failures = 0L
        private var totalDurationNanos = 0L
        private var minDurationNanos = Long.MAX_VALUE
        private var maxDurationNanos = 0L
        private var lastExecutedMillis = 0L

        @Synchronized
        fun record(success: Boolean, durationNanos: Long, timestampMillis: Long) {
            executions += 1
            if (success) {
                successes += 1
            } else {
                failures += 1
            }
            totalDurationNanos += durationNanos
            minDurationNanos = minOf(minDurationNanos, durationNanos)
            maxDurationNanos = maxOf(maxDurationNanos, durationNanos)
            lastExecutedMillis = timestampMillis
        }

        @Synchronized
        fun snapshot(path: String): ShimeCommandExecutionStats {
            return ShimeCommandExecutionStats(
                path = path,
                executions = executions,
                successes = successes,
                failures = failures,
                totalDurationNanos = totalDurationNanos,
                lastExecutedMillis = lastExecutedMillis,
                minDurationNanos = if (executions == 0L) 0L else minDurationNanos,
                maxDurationNanos = maxDurationNanos
            )
        }
    }

    private val bindings = ConcurrentHashMap<String, Binding>()
    private val cooldowns = ConcurrentHashMap<String, Long>()
    private val usageCounters = ConcurrentHashMap<String, UsageCounter>()
    private val executionStats = ConcurrentHashMap<String, MutableExecutionStats>()
    private val globalHooks = CopyOnWriteArrayList<ShimeCommandHook>()

    override fun onInitialize(context: ShimeContext) {
        // コマンドはライブラリの初期化前にプラグインのブートストラップによって登録される。
    }

    override fun onShutdown() {
        registeredNames().forEach { unregister(it) }
        cooldowns.clear()
        usageCounters.clear()
        executionStats.clear()
        globalHooks.clear()
    }

    /**
     * [root] を登録する。
     *
     * @param commandName Bukkit のコマンド名。既定ではルートノードの名前になる。
     * @param dynamic true の場合、`plugin.yml` に存在しないコマンドがサーバーのコマンドマップを通じて登録される。
     * @throws ShimeCommandException 名前が不正な場合、すでに登録済みの場合、または登録できない場合。
     */
    @JvmOverloads
    fun register(
        root: ShimeCommandNode,
        commandName: String = root.name,
        dynamic: Boolean = false
    ): ShimeCommandRegistration {
        val normalized = commandName.lowercase(Locale.ROOT)
        require(normalized.matches(Regex("[a-z0-9_-]+"))) {
            "Command name '$commandName' may only contain lowercase letters, digits, underscores and hyphens."
        }
        require(normalized !in root.aliases) { "Command '$normalized' cannot be one of its own aliases." }
        if (bindings.containsKey(normalized)) {
            throw ShimeCommandException("Command '$normalized' is already registered.")
        }

        val declared = plugin.getCommand(normalized)
        val binding = if (declared != null) {
            val dispatcher = ShimeCommandDispatcher(this, root)
            declared.setExecutor(dispatcher)
            declared.setTabCompleter(dispatcher)
            Binding(root, normalized, declared, null)
        } else {
            if (!dynamic) {
                throw ShimeCommandException(
                    "Command '$normalized' is not declared in plugin.yml. Declare it there or register it with dynamic=true."
                )
            }
            val command = ShimeBukkitCommand(
                name = normalized,
                description = root.description.ifBlank { "ShimePluginLibrary command" },
                usage = root.usageLine(normalized),
                aliases = root.aliases.toList(),
                manager = this,
                root = root
            )
            root.permission?.let(command::setPermission)
            if (!command.register(plugin.server.commandMap)) {
                throw ShimeCommandException("The server rejected registration of dynamic command '$normalized'.")
            }
            Binding(root, normalized, null, command)
        }
        bindings[normalized] = binding
        logger.info(ShimeLogCategory.COMMAND, "Registered command '/$normalized' (${root.path}).")
        return ShimeCommandRegistration(normalized, root) { unregister(normalized) }
    }

    /** コマンドを登録解除し、そのクールダウンと使用回数の状態をクリアする。 */
    fun unregister(commandName: String): Boolean {
        val normalized = commandName.lowercase(Locale.ROOT)
        val binding = bindings.remove(normalized) ?: return false
        binding.declared?.setExecutor(null)
        binding.declared?.setTabCompleter(null)
        binding.dynamicCommand?.unregister(plugin.server.commandMap)
        val pathPrefix = "|${binding.root.path}"
        cooldowns.keys.removeIf { it.endsWith(pathPrefix) || it.contains("$pathPrefix ") }
        usageCounters.keys.removeIf { it.endsWith(pathPrefix) || it.contains("$pathPrefix ") }
        executionStats.keys.removeIf { it == binding.root.path || it.startsWith("${binding.root.path} ") }
        logger.info(ShimeLogCategory.COMMAND, "Unregistered command '/$normalized'.")
        return true
    }

    fun isRegistered(commandName: String): Boolean = bindings.containsKey(commandName.lowercase(Locale.ROOT))

    fun registeredNames(): List<String> = bindings.keys.sorted()

    fun root(commandName: String): ShimeCommandNode? = bindings[commandName.lowercase(Locale.ROOT)]?.root

    /**
     * [target] のアノテーションからコマンドツリーを構築して登録する。
     *
     * @param dynamic true の場合、`plugin.yml` に存在しないコマンドも動的に登録する。
     * @throws ShimeCommandException アノテーション定義が不正な場合、または登録できない場合。
     */
    @JvmOverloads
    fun registerAnnotated(target: Any, dynamic: Boolean = false): ShimeCommandRegistration {
        return register(ShimeCommandAnnotations.build(target), dynamic = dynamic)
    }

    /** すべてのコマンドに適用される実行フックを追加する。 */
    fun addHook(hook: ShimeCommandHook) {
        globalHooks += hook
    }

    /** 登録済みのグローバル実行フックを解除する。 */
    fun removeHook(hook: ShimeCommandHook): Boolean = globalHooks.remove(hook)

    /** 登録済みのグローバル実行フックの不変ビュー。 */
    fun hooks(): List<ShimeCommandHook> = globalHooks.toList()

    /** コマンドパス別の実行統計をパス順に返す。 */
    fun statistics(): List<ShimeCommandExecutionStats> {
        return executionStats.entries
            .map { (path, stats) -> stats.snapshot(path) }
            .sortedBy { it.path }
    }

    /** [path] の実行統計を返す。未実行の場合は null。 */
    fun statistics(path: String): ShimeCommandExecutionStats? {
        return executionStats[path]?.snapshot(path)
    }

    /** すべての実行統計をクリアする。監査ログはクリアしない。 */
    fun resetStatistics() {
        executionStats.clear()
    }

    /** 1回の呼び出しを振り分ける。テストおよび独自のコマンドラッパー向けに公開されている。 */
    fun dispatch(root: ShimeCommandNode, sender: CommandSender, label: String, args: List<String>): Boolean {
        if (!canUse(sender, root)) {
            replyPermission(sender, root)
            return true
        }
        var node = root
        var index = 0
        while (index < args.size) {
            val child = node.child(args[index]) ?: break
            if (!canUse(sender, child)) {
                replyPermission(sender, child)
                return true
            }
            node = child
            index += 1
        }
        val remaining = args.drop(index)
        val executor = node.executor
        if (executor == null) {
            sendHelp(sender, node, label)
            return true
        }
        if (!node.senderType.allows(sender is Player)) {
            sender.sendMessage("This command cannot be used by this sender type.")
            return true
        }

        val senderKey = senderKey(sender)
        val cooldownKey = "$senderKey|${node.path}"
        val now = System.currentTimeMillis()
        val cooldownUntil = cooldowns[cooldownKey] ?: 0L
        if (cooldownUntil > now) {
            sender.sendMessage("Please wait ${cooldownUntil - now}ms before using this command again.")
            return true
        }
        if (!consumeExecution(senderKey, node, now)) {
            sender.sendMessage("You have used this command too many times. Try again later.")
            return true
        }

        val parseContext = ShimeCommandContext(
            sender = sender,
            root = root,
            node = node,
            label = label,
            args = remaining,
            rawArgs = args,
            manager = this,
            plugin = plugin,
            scheduler = scheduler
        )
        val arguments = LinkedHashMap<String, Any?>()
        for ((argumentIndex, spec) in node.arguments.withIndex()) {
            if (argumentIndex < remaining.size) {
                try {
                    arguments[spec.name] = spec.argument.parse(remaining[argumentIndex], parseContext)
                } catch (exception: ShimeCommandArgumentException) {
                    reportArgumentError(sender, node, label, argumentIndex, spec, remaining[argumentIndex], exception)
                    return true
                }
            } else if (spec.optional) {
                try {
                    arguments[spec.name] = spec.defaultProvider?.resolve(parseContext) ?: spec.defaultValue
                } catch (exception: ShimeCommandArgumentException) {
                    sender.sendMessage(
                        "Invalid default value for argument <${spec.name}>: ${exception.message}"
                    )
                    sender.sendMessage(node.usageLine(label))
                    return true
                }
            } else {
                sender.sendMessage("Missing required argument #${argumentIndex + 1} <${spec.name}>.")
                sender.sendMessage(node.usageLine(label))
                return true
            }
        }
        if (remaining.size > node.arguments.size) {
            val unexpectedIndex = node.arguments.size
            sender.sendMessage(
                "Too many arguments. Unexpected '${remaining[unexpectedIndex]}' at position #${unexpectedIndex + 1}."
            )
            sender.sendMessage(node.usageLine(label))
            return true
        }

        val context = ShimeCommandContext(
            sender = sender,
            root = root,
            node = node,
            label = label,
            args = remaining,
            rawArgs = args,
            manager = this,
            plugin = plugin,
            scheduler = scheduler,
            arguments = arguments
        )
        if (node.cooldownMillis > 0L) {
            cooldowns[cooldownKey] = now + node.cooldownMillis
        }

        val activeHooks = ArrayList<ShimeCommandHook>(globalHooks.size + node.commandHooks.size)
        activeHooks += globalHooks
        activeHooks += node.commandHooks
        val startedAt = System.nanoTime()
        var cancelled = false
        for (hook in activeHooks) {
            try {
                if (!hook.beforeExecution(context)) {
                    cancelled = true
                    break
                }
            } catch (throwable: Throwable) {
                logger.warn(
                    ShimeLogCategory.COMMAND,
                    "Command hook ${hook.javaClass.name} failed before '${node.path}'.",
                    throwable
                )
            }
        }

        val result: ShimeCommandResult
        var executionNanos = 0L
        if (cancelled) {
            result = ShimeCommandResult.Cancelled
        } else {
            val executionStartedAt = System.nanoTime()
            result = try {
                executor.execute(context, arguments)
            } catch (exception: ShimeCommandArgumentException) {
                ShimeCommandResult.Failure(exception.message ?: "Invalid command input.", exception)
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.COMMAND, "Command '/$label ${args.joinToString(" ")}' failed.", throwable)
                ShimeCommandResult.Failure("The command failed unexpectedly. Check the server log for details.", throwable)
            }
            executionNanos = System.nanoTime() - executionStartedAt
            activeHooks.asReversed().forEach { hook ->
                try {
                    hook.afterExecution(context, result)
                } catch (throwable: Throwable) {
                    logger.warn(
                        ShimeLogCategory.COMMAND,
                        "Command hook ${hook.javaClass.name} failed after '${node.path}'.",
                        throwable
                    )
                }
            }
            executionStats.computeIfAbsent(node.path) { MutableExecutionStats() }
                .record(result.isSuccess, executionNanos, System.currentTimeMillis())
        }
        val finishedAt = System.currentTimeMillis()
        auditLog.record(
            ShimeCommandAuditEntry(
                timestampMillis = finishedAt,
                senderName = sender.name,
                senderId = senderKey,
                commandPath = node.path,
                label = label,
                arguments = args.toList(),
                resultType = result.javaClass.simpleName,
                success = result.isSuccess,
                durationNanos = System.nanoTime() - startedAt
            )
        )
        reportResult(sender, node, label, result)
        logger.debug(
            ShimeLogCategory.COMMAND,
            "Sender ${sender.name} executed '/$label ${args.joinToString(" ")}' with result ${result.javaClass.simpleName}."
        )
        return true
    }

    /** [args] に対する権限を考慮したタブ補完候補を返す。 */
    fun complete(root: ShimeCommandNode, sender: CommandSender, args: List<String>): List<String> {
        if (!canUse(sender, root)) {
            return emptyList()
        }
        var node = root
        var index = 0
        while (index < args.size - 1) {
            val child = node.child(args[index]) ?: break
            if (!canUse(sender, child)) {
                return emptyList()
            }
            node = child
            index += 1
        }
        val partial = args.lastOrNull().orEmpty()
        val suggestions = LinkedHashSet<String>()
        node.children.filter { canUse(sender, it) }.forEach { child ->
            if (child.name.startsWith(partial, ignoreCase = true)) {
                suggestions += child.name
            }
            child.aliases.forEach { alias ->
                if (alias.startsWith(partial, ignoreCase = true)) {
                    suggestions += alias
                }
            }
        }
        val argumentIndex = args.size - 1 - index
        if (argumentIndex in node.arguments.indices) {
            val context = ShimeCommandContext(
                sender = sender,
                root = root,
                node = node,
                label = root.name,
                args = args.drop(index),
                rawArgs = args,
                manager = this,
                plugin = plugin,
                scheduler = scheduler
            )
            val spec = node.arguments[argumentIndex]
            val allowed = spec.completionPermission == null || sender.hasPermission(spec.completionPermission)
            if (allowed) {
                try {
                    suggestions += spec.argument.complete(context, partial)
                } catch (throwable: Throwable) {
                    logger.warn(ShimeLogCategory.COMMAND, "Tab completion for '${node.path}' failed.", throwable)
                }
            }
        }
        node.tabCompleter?.let { completer ->
            try {
                val context = ShimeCommandContext(
                    sender = sender,
                    root = root,
                    node = node,
                    label = root.name,
                    args = args.drop(index),
                    rawArgs = args,
                    manager = this,
                    plugin = plugin,
                    scheduler = scheduler
                )
                suggestions += completer.complete(context, partial)
            } catch (throwable: Throwable) {
                logger.warn(ShimeLogCategory.COMMAND, "Dynamic completion for '${node.path}' failed.", throwable)
            }
        }
        return suggestions
            .filter { it.startsWith(partial, ignoreCase = true) }
            .sorted()
            .take(MAX_COMPLETIONS)
    }

    /** [node] のヘルプを [sender] に送信する。 */
    fun sendHelp(sender: CommandSender, node: ShimeCommandNode, label: String = node.name) {
        node.helpLines(label).forEach(sender::sendMessage)
    }

    private fun reportArgumentError(
        sender: CommandSender,
        node: ShimeCommandNode,
        label: String,
        index: Int,
        spec: ShimeCommandArgumentSpec,
        input: String,
        exception: ShimeCommandArgumentException
    ) {
        val position = (exception.argumentIndex ?: index) + 1
        val name = exception.argumentName ?: spec.name
        val value = exception.input ?: input
        sender.sendMessage("Invalid argument #$position <$name> ('$value'): ${exception.message}")
        sender.sendMessage(node.usageLine(label))
    }

    private fun canUse(sender: CommandSender, node: ShimeCommandNode): Boolean {
        if (!node.senderType.allows(sender is Player)) {
            return false
        }
        val permission = node.permission ?: return true
        return sender.hasPermission(permission)
    }

    private fun replyPermission(sender: CommandSender, node: ShimeCommandNode) {
        val permission = node.permission
        sender.sendMessage(
            if (permission.isNullOrBlank()) {
                "You cannot use this command."
            } else {
                "You do not have permission to use this command ($permission)."
            }
        )
    }

    private fun consumeExecution(senderKey: String, node: ShimeCommandNode, now: Long): Boolean {
        if (node.executionLimit <= 0 || node.executionWindowMillis <= 0L) {
            return true
        }
        val key = "$senderKey|${node.path}|limit"
        val counter = usageCounters.computeIfAbsent(key) { UsageCounter(now, 0) }
        synchronized(counter) {
            if (now - counter.windowStart >= node.executionWindowMillis) {
                counter.windowStart = now
                counter.count = 0
            }
            if (counter.count >= node.executionLimit) {
                return false
            }
            counter.count += 1
            return true
        }
    }

    private fun reportResult(
        sender: CommandSender,
        node: ShimeCommandNode,
        label: String,
        result: ShimeCommandResult
    ) {
        when (result) {
            is ShimeCommandResult.Success -> result.message?.let(sender::sendMessage)
            is ShimeCommandResult.Failure -> {
                sender.sendMessage(result.message)
                result.cause?.let { logger.debug(ShimeLogCategory.COMMAND, "Command failure cause for '${node.path}'.", it) }
            }
            is ShimeCommandResult.Usage -> {
                result.message?.let(sender::sendMessage)
                sender.sendMessage(node.usageLine(label))
            }
            is ShimeCommandResult.NoPermission -> {
                val permission = result.permission ?: node.permission
                sender.sendMessage(
                    if (permission.isNullOrBlank()) {
                        "You do not have permission to use this command."
                    } else {
                        "You do not have permission to use this command ($permission)."
                    }
                )
            }
            ShimeCommandResult.SilentSuccess -> Unit
            ShimeCommandResult.Cancelled -> Unit
        }
    }

    private fun senderKey(sender: CommandSender): String {
        return if (sender is Player) sender.uniqueId.toString() else "console:${sender.name.lowercase(Locale.ROOT)}"
    }

    companion object {
        const val SERVICE_NAME = "command"
        private const val MAX_COMPLETIONS = 100
    }
}

/** [ShimeCommandManager.register] が返すハンドル。二重に登録解除しても安全である。 */
class ShimeCommandRegistration internal constructor(
    val commandName: String,
    val root: ShimeCommandNode,
    private val onUnregister: () -> Unit
) {
    @Volatile
    private var active = true

    val isActive: Boolean
        get() = active

    fun unregister() {
        if (active) {
            active = false
            onUnregister()
        }
    }
}

/** コマンドツリーを `plugin.yml` で宣言されたコマンドに適合させる。 */
internal class ShimeCommandDispatcher(
    private val manager: ShimeCommandManager,
    private val root: ShimeCommandNode
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        return manager.dispatch(root, sender, label, args.toList())
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return manager.complete(root, sender, args.toList())
    }
}

/** [ShimeCommandNode] に支えられた、動的に登録される Bukkit コマンド。 */
internal class ShimeBukkitCommand(
    name: String,
    description: String,
    usage: String,
    aliases: List<String>,
    private val manager: ShimeCommandManager,
    private val root: ShimeCommandNode
) : Command(name, description, usage, aliases) {
    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        return manager.dispatch(root, sender, commandLabel, args.toList())
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
        return manager.complete(root, sender, args.toList())
    }

    override fun tabComplete(
        sender: CommandSender,
        alias: String,
        args: Array<out String>,
        location: org.bukkit.Location?
    ): List<String> {
        return manager.complete(root, sender, args.toList())
    }
}
