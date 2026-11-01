package shimeserver.shimepluginlibrary.shimevalidation

import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException
import java.util.UUID
import java.util.regex.Pattern

/**
 * 依存のない小さな検証ヘルパー。
 *
 * これらの関数は、問題のあるフィールド名を含むメッセージとともに [ShimeValidationException] を投げる。
 * Minecraft サーバーなしで単体テストできるよう、意図的に Bukkit への依存を持たない。
 */
object ShimeValidation {
    private val SAFE_KEY = Pattern.compile("[A-Za-z0-9_.:-]{1,128}")
    private val SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}")

    fun <T : Any> requireNotNull(value: T?, field: String): T {
        return value ?: throw ShimeValidationException("'$field' must not be null.")
    }

    fun requireNonBlank(value: String?, field: String): String {
        val checked = requireNotNull(value, field)
        if (checked.isBlank()) {
            throw ShimeValidationException("'$field' must not be blank.")
        }
        return checked
    }

    fun requireLength(value: String, field: String, min: Int = 0, max: Int = Int.MAX_VALUE): String {
        if (value.length < min || value.length > max) {
            throw ShimeValidationException("'$field' must contain between $min and $max characters.")
        }
        return value
    }

    fun requireInRange(value: Int, field: String, min: Int, max: Int): Int {
        if (value < min || value > max) {
            throw ShimeValidationException("'$field' must be between $min and $max (received $value).")
        }
        return value
    }

    fun requireInRange(value: Long, field: String, min: Long, max: Long): Long {
        if (value < min || value > max) {
            throw ShimeValidationException("'$field' must be between $min and $max (received $value).")
        }
        return value
    }

    fun requirePositive(value: Int, field: String, allowZero: Boolean = false): Int {
        val minimum = if (allowZero) 0 else 1
        if (value < minimum) {
            throw ShimeValidationException("'$field' must be at least $minimum (received $value).")
        }
        return value
    }

    fun requirePositive(value: Long, field: String, allowZero: Boolean = false): Long {
        val minimum = if (allowZero) 0L else 1L
        if (value < minimum) {
            throw ShimeValidationException("'$field' must be at least $minimum (received $value).")
        }
        return value
    }

    fun requireSafeKey(value: String, field: String): String {
        requireNonBlank(value, field)
        if (!SAFE_KEY.matcher(value).matches()) {
            throw ShimeValidationException(
                "'$field' may only contain letters, digits, underscore, dot, colon and hyphen (max 128 characters)."
            )
        }
        return value
    }

    fun requireSafeFileName(value: String, field: String): String {
        requireNonBlank(value, field)
        if (!SAFE_FILE_NAME.matcher(value).matches() || value == "." || value == "..") {
            throw ShimeValidationException(
                "'$field' must be a simple file name using letters, digits, dot, underscore or hyphen."
            )
        }
        return value
    }

    fun requireUuid(value: String, field: String): UUID {
        return try {
            UUID.fromString(requireNonBlank(value, field))
        } catch (exception: IllegalArgumentException) {
            throw ShimeValidationException("'$field' is not a valid UUID: $value", exception)
        }
    }

    fun requireTrue(condition: Boolean, message: String) {
        if (!condition) {
            throw ShimeValidationException(message)
        }
    }
}
