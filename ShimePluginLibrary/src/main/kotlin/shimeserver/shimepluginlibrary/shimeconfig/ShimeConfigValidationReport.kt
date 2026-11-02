package shimeserver.shimepluginlibrary.shimeconfig

/**
 * 設定スキーマによる検証結果。
 *
 * [isValid] は重大な問題がないことを示します。警告だけの場合は true のままです。レポートは値そのものを
 * 保持せず、パスと説明だけを保持するため、安全にログへ出力できます。
 */
data class ShimeConfigValidationReport(
    val configName: String,
    val issues: List<ShimeConfigIssue>
) {
    val errors: List<ShimeConfigIssue>
        get() = issues.filter { it.severity == ShimeConfigSeverity.ERROR }

    val warnings: List<ShimeConfigIssue>
        get() = issues.filter { it.severity == ShimeConfigSeverity.WARNING }

    val isValid: Boolean
        get() = errors.isEmpty()

    val isClean: Boolean
        get() = issues.isEmpty()

    /** 1 行に 1 問題を並べた、ログ向けの要約。 */
    fun summary(): String {
        if (issues.isEmpty()) {
            return "Configuration '$configName' passed schema validation."
        }
        return buildString {
            append("Configuration '").append(configName).append("' has ")
            append(errors.size).append(" error(s) and ").append(warnings.size).append(" warning(s):")
            issues.forEach { append("\n  ").append(it) }
        }
    }

    override fun toString(): String = summary()
}
