package shimeserver.shimepluginlibrary.shimeconfig

/**
 * 設定ファイルの期待される構造を表すスキーマ。
 *
 * スキーマは値の型、必須かどうか、追加の制約、他の項目への依存関係を宣言します。[validate] は設定を
 * 変更せず、[applyDefaults] は不足している既定値だけを書き込みます。未知のキーは削除せず警告として
 * 報告するため、利用者が手動で追加したコメント付きの設定を壊しません。
 *
 * @param reportUnknownKeys スキーマに存在しないキーを警告として報告するか。
 */
class ShimeConfigSchema(
    val name: String,
    val entries: List<ShimeConfigEntry<*>>,
    val reportUnknownKeys: Boolean = true
) {
    private val byPath: Map<String, ShimeConfigEntry<*>>

    init {
        require(name.isNotBlank()) { "Schema name must not be blank." }
        require(entries.isNotEmpty()) { "A configuration schema must declare at least one entry." }
        val duplicates = entries.groupBy { it.path }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate configuration paths: ${duplicates.joinToString()}." }
        byPath = entries.associateBy { it.path }
    }

    /** スキーマに宣言された既定値のマップ。 */
    fun defaults(): Map<String, Any?> {
        val values = LinkedHashMap<String, Any?>()
        entries.forEach { entry ->
            entry.defaultValue?.let { values[entry.path] = it }
        }
        return values
    }

    /**
     * 不足している既定値を設定へ書き込む。
     *
     * 既存の値は上書きしません。書き込みは [ShimeConfig.set] を通るため、登録済みの変更リスナーへ通知されます。
     *
     * @return 書き込んだ件数。
     */
    fun applyDefaults(config: ShimeConfig): Int {
        var applied = 0
        entries.forEach { entry ->
            val value = entry.defaultValue ?: return@forEach
            if (!config.contains(entry.path)) {
                config.set(entry.path, value)
                applied += 1
            }
        }
        return applied
    }

    /**
     * 設定を検証し、問題の一覧を返す。
     *
     * 例外はスローしません。呼び出し側は [ShimeConfigValidationReport.isValid] を確認してから値を利用してください。
     */
    fun validate(config: ShimeConfig): ShimeConfigValidationReport {
        val issues = ArrayList<ShimeConfigIssue>()
        entries.forEach { entry ->
            validateEntry(config, entry, issues)
        }
        if (reportUnknownKeys) {
            val known = byPath.keys + "config-version"
            val knownSections = HashSet<String>()
            known.forEach { path ->
                var index = path.indexOf('.')
                while (index > 0) {
                    knownSections += path.substring(0, index)
                    index = path.indexOf('.', index + 1)
                }
            }
            config.getKeys(deep = true).forEach { key ->
                if (key !in known && key !in knownSections) {
                    issues += ShimeConfigIssue(
                        path = key,
                        message = "Key is not declared in schema '${name}'.",
                        severity = ShimeConfigSeverity.WARNING
                    )
                }
            }
        }
        return ShimeConfigValidationReport(config.name, issues)
    }

    private fun validateEntry(
        config: ShimeConfig,
        entry: ShimeConfigEntry<*>,
        issues: MutableList<ShimeConfigIssue>
    ) {
        if (!config.contains(entry.path)) {
            if (entry.required) {
                issues += ShimeConfigIssue(
                    path = entry.path,
                    message = entry.description.ifBlank { "Required value is missing." },
                    severity = ShimeConfigSeverity.ERROR
                )
            }
            return
        }
        val raw = config.get(entry.path)
        val value = entry.decode(raw)
        if (value == null) {
            issues += ShimeConfigIssue(
                path = entry.path,
                message = "Value '$raw' cannot be converted to the declared type.",
                severity = ShimeConfigSeverity.ERROR
            )
            return
        }
        entry.validateValue(value)?.let { message ->
            issues += ShimeConfigIssue(entry.path, message, ShimeConfigSeverity.ERROR)
        }
        entry.dependsOn.filterNot { config.contains(it) }.forEach { dependency ->
            issues += ShimeConfigIssue(
                path = entry.path,
                message = "Value requires '$dependency' to be present.",
                severity = ShimeConfigSeverity.ERROR
            )
        }
    }
}
