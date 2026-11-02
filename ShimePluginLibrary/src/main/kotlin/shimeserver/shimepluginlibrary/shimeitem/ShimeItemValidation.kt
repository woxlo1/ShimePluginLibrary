package shimeserver.shimepluginlibrary.shimeitem

/**
 * アイテム検証の結果。
 *
 * [valid] が false の場合、[issues] に人間が読める理由が含まれます。理由にはアイテムの表示名や Lore を
 * 含めないため、安全にログへ出力できます。
 */
data class ShimeItemValidation(
    val valid: Boolean,
    val issues: List<String>
) {
    init {
        require(valid == issues.isEmpty()) { "A valid result must not contain issues." }
    }
}
