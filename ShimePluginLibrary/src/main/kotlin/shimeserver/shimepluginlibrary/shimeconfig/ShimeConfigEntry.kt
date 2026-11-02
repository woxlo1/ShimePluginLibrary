package shimeserver.shimepluginlibrary.shimeconfig

/**
 * 型安全な設定項目の定義。
 *
 * [decoder] は YAML から読み込んだ生の値を目的の型へ変換します。変換できない場合は null を返すか例外を
 * スローしてください。スキーマ検証はどちらもエラーとして扱います。[validator] は変換後の値に対する追加の
 * 制約を返し、問題がなければ null を返します。
 *
 * @param T 変換後の型。
 * @param path YAML のドット区切りパス。
 * @param defaultValue キーが存在しない場合に使用する値。null の場合は既定値なし。
 * @param required キーが存在しない場合にエラーとするか。
 * @param dependsOn この項目が存在する場合に、同時に存在しなければならない他のパス。
 */
class ShimeConfigEntry<T : Any>(
    val path: String,
    val description: String = "",
    val defaultValue: T? = null,
    val required: Boolean = defaultValue == null,
    val dependsOn: Set<String> = emptySet(),
    private val decoder: (Any?) -> T?,
    private val validator: ((T) -> String?)? = null
) {
    init {
        require(path.isNotBlank()) { "Configuration entry path must not be blank." }
        dependsOn.forEach { require(it.isNotBlank()) { "Dependency paths must not be blank." } }
        require(path !in dependsOn) { "A configuration entry cannot depend on itself." }
    }

    /** 生の値を変換する。変換できない場合は null を返す。 */
    fun decode(value: Any?): T? {
        return try {
            decoder(value)
        } catch (_: RuntimeException) {
            null
        }
    }

    /** 変換後の値に対する追加検証。問題がなければ null。 */
    fun validateValue(value: Any?): String? {
        @Suppress("UNCHECKED_CAST")
        val typed = value as T
        return validator?.invoke(typed)
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun string(
            path: String,
            defaultValue: String? = null,
            description: String = "",
            required: Boolean = defaultValue == null,
            dependsOn: Set<String> = emptySet(),
            validator: ((String) -> String?)? = null
        ): ShimeConfigEntry<String> {
            return ShimeConfigEntry(
                path = path,
                description = description,
                defaultValue = defaultValue,
                required = required,
                dependsOn = dependsOn,
                decoder = { it as? String },
                validator = validator
            )
        }

        @JvmStatic
        @JvmOverloads
        fun integer(
            path: String,
            defaultValue: Int? = null,
            description: String = "",
            required: Boolean = defaultValue == null,
            dependsOn: Set<String> = emptySet(),
            validator: ((Int) -> String?)? = null
        ): ShimeConfigEntry<Int> {
            return ShimeConfigEntry(
                path = path,
                description = description,
                defaultValue = defaultValue,
                required = required,
                dependsOn = dependsOn,
                decoder = { value -> toInt(value) },
                validator = validator
            )
        }

        @JvmStatic
        @JvmOverloads
        fun long(
            path: String,
            defaultValue: Long? = null,
            description: String = "",
            required: Boolean = defaultValue == null,
            dependsOn: Set<String> = emptySet(),
            validator: ((Long) -> String?)? = null
        ): ShimeConfigEntry<Long> {
            return ShimeConfigEntry(
                path = path,
                description = description,
                defaultValue = defaultValue,
                required = required,
                dependsOn = dependsOn,
                decoder = { value -> toLong(value) },
                validator = validator
            )
        }

        @JvmStatic
        @JvmOverloads
        fun double(
            path: String,
            defaultValue: Double? = null,
            description: String = "",
            required: Boolean = defaultValue == null,
            dependsOn: Set<String> = emptySet(),
            validator: ((Double) -> String?)? = null
        ): ShimeConfigEntry<Double> {
            return ShimeConfigEntry(
                path = path,
                description = description,
                defaultValue = defaultValue,
                required = required,
                dependsOn = dependsOn,
                decoder = { value -> toDouble(value) },
                validator = validator
            )
        }

        @JvmStatic
        @JvmOverloads
        fun boolean(
            path: String,
            defaultValue: Boolean? = null,
            description: String = "",
            required: Boolean = defaultValue == null,
            dependsOn: Set<String> = emptySet(),
            validator: ((Boolean) -> String?)? = null
        ): ShimeConfigEntry<Boolean> {
            return ShimeConfigEntry(
                path = path,
                description = description,
                defaultValue = defaultValue,
                required = required,
                dependsOn = dependsOn,
                decoder = { value -> toBoolean(value) },
                validator = validator
            )
        }

        @JvmStatic
        @JvmOverloads
        fun stringList(
            path: String,
            defaultValue: List<String>? = null,
            description: String = "",
            required: Boolean = defaultValue == null,
            dependsOn: Set<String> = emptySet(),
            validator: ((List<String>) -> String?)? = null
        ): ShimeConfigEntry<List<String>> {
            return ShimeConfigEntry(
                path = path,
                description = description,
                defaultValue = defaultValue,
                required = required,
                dependsOn = dependsOn,
                decoder = { value ->
                    when (value) {
                        is List<*> -> value.filterNotNull().map { it.toString() }
                        else -> null
                    }
                },
                validator = validator
            )
        }

        private fun toInt(value: Any?): Int? {
            return when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }

        private fun toLong(value: Any?): Long? {
            return when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
        }

        private fun toDouble(value: Any?): Double? {
            return when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }

        private fun toBoolean(value: Any?): Boolean? {
            return when (value) {
                is Boolean -> value
                is String -> when (value.lowercase()) {
                    "true", "yes", "on" -> true
                    "false", "no", "off" -> false
                    else -> null
                }
                else -> null
            }
        }
    }
}
