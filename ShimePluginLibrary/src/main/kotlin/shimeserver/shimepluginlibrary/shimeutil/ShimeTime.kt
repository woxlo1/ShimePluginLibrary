package shimeserver.shimepluginlibrary.shimeutil

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 期間の解析と表示のヘルパー。 */
object ShimeTime {
    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val TOKEN = Regex("(\\d+)([smhdw])", RegexOption.IGNORE_CASE)

    fun formatDuration(millis: Long): String {
        var remaining = millis.coerceAtLeast(0)
        val days = TimeUnit.MILLISECONDS.toDays(remaining)
        remaining -= TimeUnit.DAYS.toMillis(days)
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        remaining -= TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
        remaining -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining)

        val parts = ArrayList<String>(4)
        if (days > 0) parts += "${days}d"
        if (hours > 0) parts += "${hours}h"
        if (minutes > 0) parts += "${minutes}m"
        if (seconds > 0 || parts.isEmpty()) parts += "${seconds}s"
        return parts.joinToString(" ")
    }

    /**
     * `30s`、`10m`、`2h30m`、`1d` のような短縮形式の期間を解析する。
     * 入力が空、または無効なトークンを含む場合は null を返す。
     */
    fun parseDuration(input: String): Long? {
        val normalized = input.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) {
            return null
        }
        val matches = TOKEN.findAll(normalized).toList()
        if (matches.isEmpty()) {
            return null
        }
        val consumed = matches.joinToString("") { it.value }
        if (consumed != normalized) {
            return null
        }
        return matches.sumOf { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return null
            when (match.groupValues[2].lowercase(Locale.ROOT)) {
                "s" -> TimeUnit.SECONDS.toMillis(amount)
                "m" -> TimeUnit.MINUTES.toMillis(amount)
                "h" -> TimeUnit.HOURS.toMillis(amount)
                "d" -> TimeUnit.DAYS.toMillis(amount)
                "w" -> TimeUnit.DAYS.toMillis(amount * 7)
                else -> return null
            }
        }
    }

    fun formatTimestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        return TIMESTAMP.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }
}
