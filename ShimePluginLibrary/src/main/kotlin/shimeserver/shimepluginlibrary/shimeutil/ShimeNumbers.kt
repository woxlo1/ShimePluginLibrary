package shimeserver.shimepluginlibrary.shimeutil

import java.math.BigDecimal
import java.math.RoundingMode

/** 通常のユーザー入力では例外を投げない、数値の解析と整形のヘルパー。 */
object ShimeNumbers {
    fun parseInt(input: String): Int? = input.trim().toIntOrNull()

    fun parseLong(input: String): Long? = input.trim().toLongOrNull()

    fun parseDecimal(input: String): BigDecimal? {
        return try {
            BigDecimal(input.trim())
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun parseDecimal(input: String, scale: Int): BigDecimal? {
        return parseDecimal(input)?.setScale(scale, RoundingMode.HALF_UP)
    }

    fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)

    fun clamp(value: Long, min: Long, max: Long): Long = value.coerceIn(min, max)

    fun format(value: BigDecimal, scale: Int = 2): String {
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString()
    }

    fun format(value: Double, scale: Int = 2): String {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString()
    }
}
