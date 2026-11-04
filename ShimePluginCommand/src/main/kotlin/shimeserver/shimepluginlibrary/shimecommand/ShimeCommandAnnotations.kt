package shimeserver.shimepluginlibrary.shimecommand

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * アノテーション方式のコマンド定義。
 *
 * クラスに [ShimeCommandDefinition] を付け、公開メソッドに [ShimeCommandHandler] を付けることで、
 * DSL を使わずにコマンドツリーを構築できる。アノテーション方式と DSL 方式はどちらも利用でき、
 * 片方だけを必須とはしない。
 *
 * ハンドラーメソッドの引数には [ShimeCommandArg] を付けた位置引数、または [ShimeCommandContext]、
 * `CommandSender`、`Map<String, Any?>` を注入できる。戻り値は [ShimeCommandResult]、`String`、
 * `Boolean`、`Unit`、`null` を扱える。
 */
object ShimeCommandAnnotations {
    private val customArguments = ConcurrentHashMap<String, ShimeArgument<*>>()

    /**
     * [name] のカスタム引数パーサーを登録する。
     *
     * [ShimeCommandArg.type] に [ShimeCommandArgumentType.CUSTOM] を指定し、[ShimeCommandArg.customType] に
     * この名前を設定すると利用できる。登録はスレッドセーフである。
     */
    @JvmStatic
    fun registerArgumentType(name: String, argument: ShimeArgument<*>) {
        val normalized = normalizeTypeName(name)
        customArguments[normalized] = argument
    }

    /** [name] のカスタム引数パーサーを解除する。 */
    @JvmStatic
    fun unregisterArgumentType(name: String): Boolean {
        return customArguments.remove(normalizeTypeName(name)) != null
    }

    /** 登録済みのカスタム引数パーサーを返す。未登録の場合は null。 */
    @JvmStatic
    fun argumentType(name: String): ShimeArgument<*>? {
        return customArguments[normalizeTypeName(name)]
    }

    /**
     * [target] のクラスとメソッドに付いたアノテーションからコマンドツリーを構築する。
     *
     * @throws ShimeCommandException 定義が不足している場合、重複したパスがある場合、引数型を解決できない場合。
     */
    @JvmStatic
    fun build(target: Any): ShimeCommandNode {
        return build(target.javaClass, target)
    }

    /**
     * [type] のアノテーションからコマンドツリーを構築する。
     *
     * [instance] は非 static ハンドラーの呼び出しに使う。すべてのハンドラーが static の場合は null を許容する。
     */
    @JvmStatic
    @JvmOverloads
    fun build(type: Class<*>, instance: Any? = null): ShimeCommandNode {
        val definition = type.getAnnotation(ShimeCommandDefinition::class.java)
            ?: throw ShimeCommandException(
                "Class ${type.name} must be annotated with @ShimeCommandDefinition."
            )
        val root = createRoot(definition)
        val handlers = type.methods
            .asSequence()
            .filterNot { it.isSynthetic || it.isBridge }
            .filter { it.isAnnotationPresent(ShimeCommandHandler::class.java) }
            .sortedWith(compareBy({ it.name }, { method -> method.parameterTypes.joinToString(",") { it.name } }))
            .toList()
        if (handlers.isEmpty()) {
            throw ShimeCommandException(
                "Class ${type.name} does not declare any @ShimeCommandHandler methods."
            )
        }

        val usedPaths = HashSet<String>()
        handlers.forEach { method ->
            val handler = method.getAnnotation(ShimeCommandHandler::class.java)
            val node = resolveNode(root, handler.path.toList())
            if (!usedPaths.add(node.path)) {
                throw ShimeCommandException("Duplicate command handler for path '${node.path}'.")
            }
            if (node.executor != null) {
                throw ShimeCommandException("Command node '${node.path}' already has an executor.")
            }
            configureNode(node, handler)
            node.executor = createExecutor(type, instance, method, node)
        }
        return root
    }

    private fun createRoot(definition: ShimeCommandDefinition): ShimeCommandNode {
        val root = ShimeCommandNode(definition.name)
        val builder = ShimeCommandBuilder(root)
        if (definition.aliases.isNotEmpty()) {
            builder.alias(*definition.aliases)
        }
        if (definition.permission.isNotBlank()) {
            builder.permission(definition.permission)
        }
        if (definition.description.isNotBlank()) {
            builder.description(definition.description)
        }
        if (definition.usage.isNotBlank()) {
            builder.usage(definition.usage)
        }
        builder.sender(definition.senderType)
        if (definition.cooldownMillis > 0L) {
            builder.cooldown(definition.cooldownMillis)
        }
        if (definition.executionLimit > 0 || definition.executionWindowMillis > 0L) {
            requireLimit(definition.executionLimit, definition.executionWindowMillis, definition.name)
            builder.limit(definition.executionLimit, definition.executionWindowMillis)
        }
        return root
    }

    private fun resolveNode(root: ShimeCommandNode, path: List<String>): ShimeCommandNode {
        var node = root
        path.forEach { segment ->
            val existing = node.child(segment)
            node = existing ?: ShimeCommandNode(segment, node).also { node.addChild(it) }
        }
        return node
    }

    private fun configureNode(node: ShimeCommandNode, handler: ShimeCommandHandler) {
        if (handler.permission.isNotBlank()) {
            node.permission = handler.permission
        }
        if (handler.description.isNotBlank()) {
            node.description = handler.description
        }
        if (handler.usage.isNotBlank()) {
            node.usage = handler.usage
        }
        if (handler.senderType != ShimeCommandSenderType.ANY) {
            node.senderType = handler.senderType
        }
        if (handler.cooldownMillis >= 0L) {
            node.cooldownMillis = handler.cooldownMillis
        }
        if (handler.executionLimit >= 0 || handler.executionWindowMillis >= 0L) {
            requireLimit(handler.executionLimit, handler.executionWindowMillis, node.path)
            node.executionLimit = handler.executionLimit
            node.executionWindowMillis = handler.executionWindowMillis
        }
    }

    private fun requireLimit(limit: Int, windowMillis: Long, path: String) {
        require(limit > 0) { "Execution limit for '$path' must be positive when configured." }
        require(windowMillis > 0L) { "Execution window for '$path' must be positive when configured." }
    }

    private fun createExecutor(
        type: Class<*>,
        instance: Any?,
        method: java.lang.reflect.Method,
        node: ShimeCommandNode
    ): ShimeCommandExecutor {
        if (!java.lang.reflect.Modifier.isStatic(method.modifiers) && instance == null) {
            throw ShimeCommandException(
                "Handler ${type.name}#${method.name} requires an instance for annotation registration."
            )
        }
        if (!method.trySetAccessible()) {
            throw ShimeCommandException(
                "Handler ${type.name}#${method.name} is not accessible for annotation registration."
            )
        }

        val parameters = method.parameters.mapIndexed { index, parameter ->
            val argument = parameter.getAnnotation(ShimeCommandArg::class.java)
            if (argument == null) {
                injectableParameter(type, method, parameter, index)
            } else {
                argumentParameter(type, method, parameter, argument, index)
            }
        }
        node.arguments += parameters.mapNotNull { it.spec }

        return ShimeCommandExecutor { context, values ->
            val invocationArguments = parameters.map { parameter ->
                when (parameter.kind) {
                    HandlerParameterKind.CONTEXT -> context
                    HandlerParameterKind.SENDER -> context.sender
                    HandlerParameterKind.ARGUMENTS -> values
                    HandlerParameterKind.ARGUMENT -> {
                        val name = parameter.spec!!.name
                        if (values.containsKey(name)) {
                            values[name]
                        } else if (parameter.spec.optional) {
                            parameter.spec.defaultProvider?.resolve(context) ?: parameter.spec.defaultValue
                        } else {
                            throw ShimeCommandArgumentException(
                                "Missing required argument '${parameter.spec.name}'.",
                                argumentName = parameter.spec.name
                            )
                        }
                    }
                }
            }
            invokeHandler(instance, method, invocationArguments.toTypedArray())
        }
    }

    private fun injectableParameter(
        type: Class<*>,
        method: java.lang.reflect.Method,
        parameter: java.lang.reflect.Parameter,
        index: Int
    ): HandlerParameter {
        return when {
            ShimeCommandContext::class.java.isAssignableFrom(parameter.type) -> HandlerParameter(
                HandlerParameterKind.CONTEXT,
                null
            )
            org.bukkit.command.CommandSender::class.java.isAssignableFrom(parameter.type) -> HandlerParameter(
                HandlerParameterKind.SENDER,
                null
            )
            Map::class.java.isAssignableFrom(parameter.type) -> HandlerParameter(
                HandlerParameterKind.ARGUMENTS,
                null
            )
            else -> throw ShimeCommandException(
                "Parameter #${index + 1} of ${type.name}#${method.name} must be annotated with " +
                    "@ShimeCommandArg or be ShimeCommandContext, CommandSender, or Map<String, Any?>."
            )
        }
    }

    private fun argumentParameter(
        type: Class<*>,
        method: java.lang.reflect.Method,
        parameter: java.lang.reflect.Parameter,
        annotation: ShimeCommandArg,
        index: Int
    ): HandlerParameter {
        val name = annotation.name.ifBlank {
            parameter.name.takeUnless { it.matches(Regex("arg\\d+")) } ?: "arg${index + 1}"
        }
        val argument = resolveArgument(type, method, parameter, annotation)
        val optional = annotation.optional || annotation.hasDefault
        val spec = ShimeCommandArgumentSpec(
            name = name,
            argument = argument,
            optional = optional,
            defaultProvider = if (annotation.hasDefault) {
                ShimeCommandDefaultProvider { context -> argument.parse(annotation.defaultValue, context) }
            } else {
                null
            },
            completionPermission = annotation.completionPermission.takeIf { it.isNotBlank() }
        )
        return HandlerParameter(HandlerParameterKind.ARGUMENT, spec)
    }

    private fun resolveArgument(
        type: Class<*>,
        method: java.lang.reflect.Method,
        parameter: java.lang.reflect.Parameter,
        annotation: ShimeCommandArg
    ): ShimeArgument<*> {
        return when (annotation.type) {
            ShimeCommandArgumentType.AUTO -> autoArgument(type, method, parameter, annotation)
            ShimeCommandArgumentType.STRING -> ShimeArgumentTypes.string()
            ShimeCommandArgumentType.INTEGER -> ShimeArgumentTypes.integer(
                integerBound(annotation.min, "min", type, method),
                integerBound(annotation.max, "max", type, method)
            )
            ShimeCommandArgumentType.LONG -> ShimeArgumentTypes.long(
                longBound(annotation.min, "min", type, method),
                longBound(annotation.max, "max", type, method)
            )
            ShimeCommandArgumentType.DECIMAL -> ShimeArgumentTypes.decimal(
                decimalBound(annotation.min, "min", type, method),
                decimalBound(annotation.max, "max", type, method)
            )
            ShimeCommandArgumentType.BOOLEAN -> ShimeArgumentTypes.boolean()
            ShimeCommandArgumentType.UUID -> ShimeArgumentTypes.uuid()
            ShimeCommandArgumentType.PLAYER -> ShimeArgumentTypes.player()
            ShimeCommandArgumentType.OFFLINE_PLAYER -> ShimeArgumentTypes.offlinePlayer()
            ShimeCommandArgumentType.WORLD -> ShimeArgumentTypes.world()
            ShimeCommandArgumentType.ENUM -> enumerationArgument(type, method, parameter)
            ShimeCommandArgumentType.CHOICE -> {
                if (annotation.choices.isEmpty()) {
                    throw ShimeCommandException(
                        "Parameter ${parameter.name} of ${type.name}#${method.name} uses CHOICE without choices."
                    )
                }
                ShimeArgumentTypes.choice(annotation.choices.toList())
            }
            ShimeCommandArgumentType.CUSTOM -> {
                if (annotation.customType.isBlank()) {
                    throw ShimeCommandException(
                        "Parameter ${parameter.name} of ${type.name}#${method.name} uses CUSTOM without customType."
                    )
                }
                customArguments[normalizeTypeName(annotation.customType)]
                    ?: throw ShimeCommandException(
                        "Custom argument type '${annotation.customType}' is not registered."
                    )
            }
        }
    }

    private fun autoArgument(
        type: Class<*>,
        method: java.lang.reflect.Method,
        parameter: java.lang.reflect.Parameter,
        annotation: ShimeCommandArg
    ): ShimeArgument<*> {
        return when {
            parameter.type == String::class.java -> ShimeArgumentTypes.string()
            parameter.type == Int::class.javaPrimitiveType || parameter.type == Integer::class.java ->
                ShimeArgumentTypes.integer(
                    integerBound(annotation.min, "min", type, method),
                    integerBound(annotation.max, "max", type, method)
                )
            parameter.type == Long::class.javaPrimitiveType || parameter.type == java.lang.Long::class.java ->
                ShimeArgumentTypes.long(
                    longBound(annotation.min, "min", type, method),
                    longBound(annotation.max, "max", type, method)
                )
            parameter.type == Double::class.javaPrimitiveType || parameter.type == java.lang.Double::class.java ->
                ShimeArgumentTypes.decimal(
                    decimalBound(annotation.min, "min", type, method),
                    decimalBound(annotation.max, "max", type, method)
                )
            parameter.type == Boolean::class.javaPrimitiveType || parameter.type == java.lang.Boolean::class.java ->
                ShimeArgumentTypes.boolean()
            parameter.type == java.util.UUID::class.java -> ShimeArgumentTypes.uuid()
            org.bukkit.entity.Player::class.java.isAssignableFrom(parameter.type) -> ShimeArgumentTypes.player()
            org.bukkit.OfflinePlayer::class.java.isAssignableFrom(parameter.type) ->
                ShimeArgumentTypes.offlinePlayer()
            org.bukkit.World::class.java.isAssignableFrom(parameter.type) -> ShimeArgumentTypes.world()
            parameter.type.isEnum -> enumerationArgument(type, method, parameter)
            else -> throw ShimeCommandException(
                "Cannot determine argument type for parameter ${parameter.name} of " +
                    "${type.name}#${method.name}. Specify @ShimeCommandArg(type = ...)."
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun enumerationArgument(
        type: Class<*>,
        method: java.lang.reflect.Method,
        parameter: java.lang.reflect.Parameter
    ): ShimeArgument<*> {
        if (!parameter.type.isEnum) {
            throw ShimeCommandException(
                "Parameter ${parameter.name} of ${type.name}#${method.name} is not an enum."
            )
        }
        return ShimeArgumentTypes.enumArgument(parameter.type)
    }

    private fun integerBound(value: String, name: String, type: Class<*>, method: java.lang.reflect.Method): Int? {
        if (value.isBlank()) {
            return null
        }
        return value.toIntOrNull()
            ?: throw ShimeCommandException("Invalid $name bound '$value' on ${type.name}#${method.name}.")
    }

    private fun longBound(value: String, name: String, type: Class<*>, method: java.lang.reflect.Method): Long? {
        if (value.isBlank()) {
            return null
        }
        return value.toLongOrNull()
            ?: throw ShimeCommandException("Invalid $name bound '$value' on ${type.name}#${method.name}.")
    }

    private fun decimalBound(value: String, name: String, type: Class<*>, method: java.lang.reflect.Method): Double? {
        if (value.isBlank()) {
            return null
        }
        return value.toDoubleOrNull()
            ?: throw ShimeCommandException("Invalid $name bound '$value' on ${type.name}#${method.name}.")
    }

    private fun invokeHandler(instance: Any?, method: java.lang.reflect.Method, arguments: Array<Any?>): ShimeCommandResult {
        val value = try {
            method.invoke(instance, *arguments)
        } catch (exception: java.lang.reflect.InvocationTargetException) {
            throw exception.targetException
        } catch (exception: IllegalAccessException) {
            throw ShimeCommandException(
                "Could not invoke handler ${method.declaringClass.name}#${method.name}.",
                exception
            )
        }
        return when (value) {
            null -> ShimeCommandResult.SilentSuccess
            is ShimeCommandResult -> value
            is String -> ShimeCommandResult.Success(value)
            is Boolean -> if (value) ShimeCommandResult.SilentSuccess else ShimeCommandResult.Failure("Command returned false.")
            else -> ShimeCommandResult.SilentSuccess
        }
    }

    private fun normalizeTypeName(name: String): String {
        require(name.isNotBlank()) { "Custom argument type name must not be blank." }
        return name.lowercase(Locale.ROOT)
    }

    private enum class HandlerParameterKind {
        CONTEXT,
        SENDER,
        ARGUMENTS,
        ARGUMENT
    }

    private class HandlerParameter(
        val kind: HandlerParameterKind,
        val spec: ShimeCommandArgumentSpec?
    )
}
