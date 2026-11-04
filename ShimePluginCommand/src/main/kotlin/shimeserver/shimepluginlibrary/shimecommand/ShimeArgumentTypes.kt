package shimeserver.shimepluginlibrary.shimecommand

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID

/**
 * コマンド DSL が使用する標準の引数パーサー群。
 *
 * 各パーサーは入力を検証し、コマンド送信者に見せられるメッセージを持つ [ShimeCommandArgumentException] を
 * スローする。補完メソッドは読み取り専用であり、サーバー状態を変更することは決してない。
 */
object ShimeArgumentTypes {
    /** 空白以外の文字を含む文字列。 */
    @JvmStatic
    fun string(): ShimeArgument<String> = argument("text") { input ->
        input.takeIf { it.isNotBlank() } ?: throw ShimeCommandArgumentException("A non-blank text value is required.")
    }

    /** 整数。任意で包含範囲を指定できる。 */
    @JvmStatic
    fun integer(min: Int? = null, max: Int? = null): ShimeArgument<Int> {
        return argument("number") { input ->
            val value = input.toIntOrNull()
                ?: throw ShimeCommandArgumentException("'$input' is not a valid integer.")
            if (min != null && value < min) {
                throw ShimeCommandArgumentException("Value must be at least $min.")
            }
            if (max != null && value > max) {
                throw ShimeCommandArgumentException("Value must be at most $max.")
            }
            value
        }
    }

    /** 長整数。任意で包含範囲を指定できる。 */
    @JvmStatic
    fun long(min: Long? = null, max: Long? = null): ShimeArgument<Long> {
        return argument("number") { input ->
            val value = input.toLongOrNull()
                ?: throw ShimeCommandArgumentException("'$input' is not a valid whole number.")
            if (min != null && value < min) {
                throw ShimeCommandArgumentException("Value must be at least $min.")
            }
            if (max != null && value > max) {
                throw ShimeCommandArgumentException("Value must be at most $max.")
            }
            value
        }
    }

    /** 小数。任意で包含範囲を指定できる。 */
    @JvmStatic
    fun decimal(min: Double? = null, max: Double? = null): ShimeArgument<Double> {
        return argument("decimal") { input ->
            val value = input.toDoubleOrNull()
                ?: throw ShimeCommandArgumentException("'$input' is not a valid decimal number.")
            if (!value.isFinite()) {
                throw ShimeCommandArgumentException("Value must be finite.")
            }
            if (min != null && value < min) {
                throw ShimeCommandArgumentException("Value must be at least $min.")
            }
            if (max != null && value > max) {
                throw ShimeCommandArgumentException("Value must be at most $max.")
            }
            value
        }
    }

    /** true/false、yes/no、on/off を受け付ける真偽値。 */
    @JvmStatic
    fun boolean(): ShimeArgument<Boolean> {
        return argument("true|false", completer = { _, partial ->
            listOf("true", "false").filter { it.startsWith(partial, ignoreCase = true) }
        }) { input ->
            when (input.lowercase()) {
                "true", "yes", "on" -> true
                "false", "no", "off" -> false
                else -> throw ShimeCommandArgumentException("'$input' is not a valid boolean (true/false).")
            }
        }
    }

    /** 正規形式の UUID。 */
    @JvmStatic
    fun uuid(): ShimeArgument<UUID> = argument("uuid") { input ->
        try {
            UUID.fromString(input)
        } catch (exception: IllegalArgumentException) {
            throw ShimeCommandArgumentException("'$input' is not a valid UUID.", exception)
        }
    }

    /** オンラインのプレイヤー。完全一致する名前で検索し、その後大文字小文字を区別せずに照合する。 */
    @JvmStatic
    fun player(): ShimeArgument<Player> = argument(
        "player",
        completer = { _, partial ->
            Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(partial, ignoreCase = true) }
        }
    ) { input ->
        Bukkit.getPlayerExact(input)
            ?: Bukkit.getOnlinePlayers().firstOrNull { it.name.equals(input, ignoreCase = true) }
            ?: throw ShimeCommandArgumentException("Player '$input' is not online.")
    }

    /** オフラインのプレイヤー。[requirePlayedBefore] が true の場合、未知の名前は拒否される。 */
    @JvmStatic
    @JvmOverloads
    fun offlinePlayer(requirePlayedBefore: Boolean = true): ShimeArgument<OfflinePlayer> {
        return argument("player") { input ->
            val player = Bukkit.getOfflinePlayer(input)
            if (requirePlayedBefore && !player.hasPlayedBefore() && !player.isOnline) {
                throw ShimeCommandArgumentException("Player '$input' has never joined this server.")
            }
            player
        }
    }

    /** ロード済みのワールド。 */
    @JvmStatic
    fun world(): ShimeArgument<World> = argument(
        "world",
        completer = { _, partial ->
            Bukkit.getWorlds().map { it.name }.filter { it.startsWith(partial, ignoreCase = true) }
        }
    ) { input ->
        Bukkit.getWorld(input)
            ?: throw ShimeCommandArgumentException("World '$input' is not loaded.")
    }

    /** 列挙定数。大文字小文字を区別せずに照合する。 */
    @JvmStatic
    fun <E : Enum<E>> enumeration(type: Class<E>): ShimeArgument<E> {
        val constants = type.enumConstants
        return argument(
            type.simpleName,
            completer = { _, partial ->
                constants.map { it.name }.filter { it.startsWith(partial, ignoreCase = true) }
            }
        ) { input ->
            constants.firstOrNull { it.name.equals(input, ignoreCase = true) }
                ?: throw ShimeCommandArgumentException(
                    "'$input' is not a valid ${type.simpleName}. Valid values: " +
                        constants.joinToString { it.name } + "."
                )
        }
    }

    /**
     * 列挙型を型消去して扱うパーサー。アノテーション方式のように、実行時の `Class` から
     * 列挙値を受け取る必要がある場合に使う。
     */
    @JvmStatic
    fun enumArgument(type: Class<*>): ShimeArgument<Enum<*>> {
        require(type.isEnum) { "${type.name} is not an enum type." }
        val constants = type.enumConstants.map { it as Enum<*> }
        return argument(
            type.simpleName,
            completer = { _, partial ->
                constants.map { it.name }.filter { it.startsWith(partial, ignoreCase = true) }
            }
        ) { input ->
            constants.firstOrNull { it.name.equals(input, ignoreCase = true) }
                ?: throw ShimeCommandArgumentException(
                    "'$input' is not a valid ${type.simpleName}. Valid values: " +
                        constants.joinToString { it.name } + "."
                )
        }
    }

    /** 固定された文字列集合のうちの1つ。[values] に含まれる正規の表記が返される。 */
    @JvmStatic
    fun choice(values: Collection<String>): ShimeArgument<String> {
        require(values.isNotEmpty()) { "Choice argument requires at least one value." }
        val snapshot = values.toList()
        return argument(
            "choice",
            completer = { _, partial -> snapshot.filter { it.startsWith(partial, ignoreCase = true) } }
        ) { input ->
            snapshot.firstOrNull { it.equals(input, ignoreCase = true) }
                ?: throw ShimeCommandArgumentException(
                    "'$input' is not valid. Valid values: ${snapshot.joinToString()}."
                )
        }
    }

    /** 呼び出し側が指定するパーサー。任意で補完プロバイダーを指定できる。 */
    @JvmStatic
    @JvmOverloads
    fun <T> custom(
        name: String,
        parser: (String, ShimeCommandContext) -> T,
        completer: ((ShimeCommandContext, String) -> List<String>)? = null
    ): ShimeArgument<T> {
        return object : ShimeArgument<T> {
            override val name: String = name.ifBlank { "value" }

            override fun parse(input: String, context: ShimeCommandContext): T {
                return try {
                    parser(input, context)
                } catch (exception: ShimeCommandArgumentException) {
                    throw exception
                } catch (exception: Exception) {
                    throw ShimeCommandArgumentException("Could not parse '$input' as $name.", exception)
                }
            }

            override fun complete(context: ShimeCommandContext, partial: String): List<String> {
                return completer?.invoke(context, partial).orEmpty()
            }
        }
    }

    private fun <T> argument(
        name: String,
        completer: ((ShimeCommandContext, String) -> List<String>)? = null,
        parser: (String) -> T
    ): ShimeArgument<T> {
        return object : ShimeArgument<T> {
            override val name: String = name

            override fun parse(input: String, context: ShimeCommandContext): T {
                return try {
                    parser(input)
                } catch (exception: ShimeCommandArgumentException) {
                    throw exception
                } catch (exception: Exception) {
                    throw ShimeCommandArgumentException("Could not parse '$input' as $name.", exception)
                }
            }

            override fun complete(context: ShimeCommandContext, partial: String): List<String> {
                return completer?.invoke(context, partial).orEmpty()
            }
        }
    }
}
