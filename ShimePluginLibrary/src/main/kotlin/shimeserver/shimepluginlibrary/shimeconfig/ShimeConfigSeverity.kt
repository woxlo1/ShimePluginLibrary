package shimeserver.shimepluginlibrary.shimeconfig

/** 設定検証レポート内の重要度。 */
enum class ShimeConfigSeverity {
    /** 設定が意図したとおりに動作しない可能性がある問題。 */
    ERROR,

    /** 動作は継続できるが、修正を推奨する問題。 */
    WARNING
}
