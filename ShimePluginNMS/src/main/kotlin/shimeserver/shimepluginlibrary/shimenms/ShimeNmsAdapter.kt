package shimeserver.shimepluginlibrary.shimenms

import shimeserver.shimepluginlibrary.shimepacket.ShimePacketTransport

/**
 * バージョン固有の NMS 実装。
 *
 * アダプターは [ShimeNmsManager.registerAdapter] または Java の [java.util.ServiceLoader]
 * メカニズムを通じて検出される。アダプターは、[supportedMinecraftVersions] の各バージョンについて
 * 実際に検証済みの [ShimeNmsFeature] だけを宣言しなければならない。ライブラリがバージョン番号だけから
 * サポートを推測することはない。
 *
 * 実装は [initialize] が戻った後はスレッドセーフでなければならず、[shutdown] は冪等でなければならない。
 */
interface ShimeNmsAdapter {
    /** ログと診断に使用される安定した識別子。 */
    val adapterId: String

    /** このアダプターが対象としている Minecraft バージョン。 */
    val supportedMinecraftVersions: Set<ShimeMinecraftVersion>

    /** このアダプターが [version] 向けかどうかを返す。 */
    fun supportsVersion(version: ShimeNmsVersion): Boolean {
        val minecraft = version.minecraftVersion ?: return false
        return minecraft in supportedMinecraftVersions
    }

    /** [feature] が実装され、検出されたバージョンで検証済みかどうかを返す。 */
    fun supports(feature: ShimeNmsFeature): Boolean

    /**
     * アダプターを初期化する。
     *
     * 使用可能になった時点で、アダプターは自身のステータスを [ShimeNmsAdapterStatus.COMPATIBLE] に設定しなければならない。
     * バージョン不一致は例外を投げるのではなく [ShimeNmsAdapterStatus.UNSUPPORTED_VERSION] として
     * 報告すべきである。これにより、マネージャーは別のアダプターを試せる。
     */
    fun initialize(context: ShimeNmsAdapterContext)

    /** アダプターが保持するすべてのリソースを解放する。冪等でなければならない。 */
    fun shutdown()

    /** 現在のアダプターステータスを返す。 */
    fun status(): ShimeNmsAdapterStatus

    /**
     * このアダプターがパケットの送信または受信を実装している場合、パケットトランスポートを返す。
     *
     * `null` を返すことが誠実なデフォルトである。その場合、ライブラリは黙って何もしないのではなく、
     * 明確なエラーでパケット操作を拒否する。
     */
    fun packetTransport(): ShimePacketTransport? = null
}
