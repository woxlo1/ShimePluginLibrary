package shimeserver.shimepluginlibrary.shimeutil

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/** テキスト変換とプレースホルダー置換のヘルパー。 */
object ShimeText {
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()
    private val ampersandSerializer = LegacyComponentSerializer.legacyAmpersand()

    fun component(input: String): Component {
        return try {
            miniMessage.deserialize(input)
        } catch (_: RuntimeException) {
            Component.text(input)
        }
    }

    fun legacy(input: String): String = legacySerializer.serialize(component(input))

    fun legacyToComponent(input: String): Component = legacySerializer.deserialize(input)

    fun ampersand(input: String): Component = ampersandSerializer.deserialize(input)

    fun plain(input: String): String = PlainTextComponentSerializer.plainText().serialize(component(input))

    fun plain(component: Component): String = PlainTextComponentSerializer.plainText().serialize(component)

    fun stripTags(input: String): String = plain(input)

    /**
     * `{name}` 形式と `%name%` 形式の両方を置換する。値は呼び出し側によってリテラルテキストとして挿入され、
     * このメソッドは置換後の文字列を MiniMessage として解釈しない。
     */
    fun replace(input: String, values: Map<String, String>): String {
        if (values.isEmpty()) {
            return input
        }
        var result = input
        for ((key, value) in values) {
            result = result.replace("{$key}", value).replace("%$key%", value)
        }
        return result
    }

    fun joinLines(lines: Iterable<String>, separator: String = "\n"): String = lines.joinToString(separator)

    fun truncate(input: String, maxLength: Int): String {
        if (maxLength <= 0) {
            return ""
        }
        return if (input.length <= maxLength) input else input.substring(0, maxLength)
    }
}
