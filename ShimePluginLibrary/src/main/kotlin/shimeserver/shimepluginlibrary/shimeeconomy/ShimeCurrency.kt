package shimeserver.shimepluginlibrary.shimeeconomy

/** 通貨のメタデータ。金額は常に [scale] 桁の小数を持つ BigDecimal で表現されます。 */
data class ShimeCurrency(
    val code: String = "COIN",
    val symbol: String = "$",
    val scale: Int = 2
) {
    init {
        require(code.isNotBlank()) { "Currency code must not be blank." }
        require(scale in 0..8) { "Currency scale must be between 0 and 8." }
    }
}
