package shimeserver.shimepluginlibrary.shimenms

import org.bukkit.Server

/**
 * 公開されたサーバー文字列から、稼働中の Minecraft バージョンと CraftBukkit のパッケージバージョンを検出する。
 *
 * 検出には、サーバーが API とクラス名を通じて公開している値のみを使用する。内部クラスをロードしたり
 * 推測したりすることは決してない。純粋なオーバーロードが存在するのは、稼働中のサーバーなしで
 * 解析ルールを単体テストできるようにするためである。
 */
object ShimeVersionDetector {
    private val SERVER_VERSION_PATTERN = Regex("""\(MC:\s*([0-9][0-9.]*)\)""")
    private val NMS_PACKAGE_PATTERN = Regex("""\.(v\d+_\d+_R\d+)\.?""")

    /**
     * 稼働中の [server] からバージョンを検出する。
     *
     * このメソッドはプラグインの起動中に呼び出しても安全である。NMS クラスには触れない。
     */
    @JvmStatic
    fun detect(server: Server): ShimeNmsVersion {
        return detect(
            bukkitVersion = server.bukkitVersion,
            serverVersion = server.version,
            serverClassName = server.javaClass.name
        )
    }

    /**
     * 生の文字列からバージョンを検出する。
     *
     * @param bukkitVersion [Server.getBukkitVersion] が返す値（例: `1.20.6-R0.1-SNAPSHOT`）。
     * @param serverVersion [Server.getVersion] が返す値（例: `git-Paper-151 (MC: 1.20.6)`）。
     * @param serverClassName サーバー実装の実行時クラス名。
     */
    @JvmStatic
    fun detect(bukkitVersion: String, serverVersion: String, serverClassName: String): ShimeNmsVersion {
        val fromBukkit = ShimeMinecraftVersion.parseOrNull(bukkitVersion)
        val fromServer = SERVER_VERSION_PATTERN.find(serverVersion)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(ShimeMinecraftVersion::parseOrNull)
        val nmsPackage = NMS_PACKAGE_PATTERN.find(serverClassName)?.groupValues?.getOrNull(1)
        return ShimeNmsVersion(
            minecraftVersion = fromBukkit ?: fromServer,
            bukkitVersion = bukkitVersion,
            serverVersion = serverVersion,
            serverImplementation = serverClassName,
            nmsPackage = nmsPackage
        )
    }
}
