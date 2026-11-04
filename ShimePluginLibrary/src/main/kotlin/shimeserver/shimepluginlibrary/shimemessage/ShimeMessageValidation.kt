package shimeserver.shimepluginlibrary.shimemessage

/**
 * メッセージの登録済みテンプレート。
 *
 * [requiredPlaceholders] が空の場合、テンプレート文字列から `{name}` / `%name%` 形式の
 * プレースホルダーを自動抽出します。
 */
data class ShimeMessageTemplate(
    val key: String,
    val defaultText: String,
    val requiredPlaceholders: Set<String> = emptySet()
) {
    init {
        require(key.isNotBlank()) { "Message template key must not be blank." }
        require(defaultText.isNotEmpty()) { "Message template text must not be empty." }
    }

    /** 実際に要求されるプレースホルダー。 */
    fun placeholders(): Set<String> {
        if (requiredPlaceholders.isNotEmpty()) {
            return requiredPlaceholders.toSet()
        }
        val pattern = Regex("[{%]([A-Za-z0-9_.-]+)[}%]")
        return pattern.findAll(defaultText).map { it.groupValues[1] }.toSet()
    }
}

/** メッセージ検証で見つかった問題の種別。 */
enum class ShimeMessageIssueType {
    MISSING_KEY,
    MISSING_PLACEHOLDER,
    FORMAT_ERROR
}

/** メッセージ検証で見つかった問題 1 件。 */
data class ShimeMessageIssue(
    val type: ShimeMessageIssueType,
    val message: String,
    val placeholder: String? = null
)

/** メッセージ検証の結果。 */
data class ShimeMessageValidationReport(
    val locale: String,
    val key: String,
    val issues: List<ShimeMessageIssue>
) {
    val isValid: Boolean
        get() = issues.isEmpty()

    val missingPlaceholders: Set<String>
        get() = issues.filter { it.type == ShimeMessageIssueType.MISSING_PLACEHOLDER }
            .mapNotNull { it.placeholder }
            .toSet()

    /** 人が読める形の要約。 */
    fun summary(): String {
        if (isValid) {
            return "Message '$key' for locale '$locale' is valid."
        }
        return issues.joinToString("; ") { it.message }
    }
}
