package shimeserver.shimepluginlibrary.shimenms

import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException

/**
 * major・minor・patch の各要素から構成される、検証済みの Minecraft バージョン。
 *
 * インスタンスは不変かつ比較可能であるため、
 * `version.isAtLeast(ShimeMinecraftVersion(1, 20, 6))` のような機能ゲートを、稼働中のサーバーなしで
 * 明示的に記述しテストできる。
 */
data class ShimeMinecraftVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<ShimeMinecraftVersion> {
    init {
        require(major >= 0) { "Major version must not be negative." }
        require(minor >= 0) { "Minor version must not be negative." }
        require(patch >= 0) { "Patch version must not be negative." }
    }

    override fun compareTo(other: ShimeMinecraftVersion): Int {
        val majorComparison = major.compareTo(other.major)
        if (majorComparison != 0) {
            return majorComparison
        }
        val minorComparison = minor.compareTo(other.minor)
        if (minorComparison != 0) {
            return minorComparison
        }
        return patch.compareTo(other.patch)
    }

    /** このバージョンが [other] 以上かどうかを返す。 */
    fun isAtLeast(other: ShimeMinecraftVersion): Boolean = this >= other

    /** このバージョンが [other] より小さいかどうかを返す。 */
    fun isBefore(other: ShimeMinecraftVersion): Boolean = this < other

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** 初期サポート対象のサーバーバージョン。 */
        @JvmField
        val PAPER_1_20_6: ShimeMinecraftVersion = ShimeMinecraftVersion(1, 20, 6)

        private val VERSION_PATTERN = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")

        /**
         * [value] 内で最初に見つかったセマンティックバージョンを解析する。
         *
         * `1.20.6-R0.1-SNAPSHOT` のような Bukkit のバージョン文字列や、
         * `git-Paper-151 (MC: 1.20.6)` のようなサーバーのバージョン文字列をどちらも受け付ける。
         * バージョンが含まれていない場合は `null` を返す。
         */
        @JvmStatic
        fun parseOrNull(value: String): ShimeMinecraftVersion? {
            val match = VERSION_PATTERN.find(value) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: 0
            return ShimeMinecraftVersion(major, minor, patch)
        }

        /**
         * [value] を解析し、入力を特定できるメッセージを持つ [ShimeValidationException] を投げる。
         */
        @JvmStatic
        fun parse(value: String): ShimeMinecraftVersion {
            return parseOrNull(value) ?: throw ShimeValidationException(
                "Cannot parse a Minecraft version from '$value'."
            )
        }
    }
}
