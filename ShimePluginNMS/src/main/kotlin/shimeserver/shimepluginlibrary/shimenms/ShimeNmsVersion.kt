package shimeserver.shimepluginlibrary.shimenms

/**
 * 実行時のバージョン検出結果を表す不変オブジェクト。
 *
 * [nmsPackage] は、実行時にサーバー実装のクラス名から読み取られた CraftBukkit のパッケージバージョン
 * （例: `v1_20_R4`）である。サーバーがバージョン付きパッケージを公開していない場合は `null` となり、
 * これは CraftBukkit 以外の実装では正常である。ライブラリがこの値を推測することはない。
 */
class ShimeNmsVersion(
    val minecraftVersion: ShimeMinecraftVersion?,
    val bukkitVersion: String,
    val serverVersion: String,
    val serverImplementation: String,
    val nmsPackage: String?
) {
    /** サーバーの文字列から Minecraft バージョンを解析できたかどうかを返す。 */
    val isDetected: Boolean
        get() = minecraftVersion != null

    /**
     * このサーバーバージョンが組み込みアダプターによって明示的にサポートされているかどうかを返す。
     *
     * 初期状態では [ShimeMinecraftVersion.PAPER_1_20_6] のみがサポートされる。外部アダプターが
     * サポートを宣言している場合は、より新しいサーバーでも動作する可能性がある。
     */
    val isSupported: Boolean
        get() = minecraftVersion == ShimeMinecraftVersion.PAPER_1_20_6

    /** 検出された場合、完全修飾された NMS パッケージを返す。 */
    val nmsPackageName: String?
        get() = nmsPackage?.let { "net.minecraft.server.$it" }

    /** ログと診断で使用される人間が読める形式のサマリー。 */
    fun summary(): String {
        val versionText = minecraftVersion?.toString() ?: "unknown"
        val packageText = nmsPackage ?: "unavailable"
        return "Minecraft $versionText (Bukkit '$bukkitVersion', server '$serverVersion', " +
            "implementation '$serverImplementation', NMS package '$packageText')"
    }

    override fun toString(): String = summary()
}
