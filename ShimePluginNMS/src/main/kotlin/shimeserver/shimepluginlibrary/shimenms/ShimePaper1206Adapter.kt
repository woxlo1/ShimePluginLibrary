package shimeserver.shimepluginlibrary.shimenms

import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory

/**
 * 初期サポート対象のサーバーバージョン向けの組み込みアダプター。
 *
 * 意図的にサーバー内部を使用しない。検出されたバージョンがサポート対象であることを確認し、
 * [ShimeNmsFeature.VERSION_DETECTION] のみを報告する。パケット、エンティティ、表示に関する機能は、
 * 別途検証されたアダプターが登録するまで利用できないままである。
 */
class ShimePaper1206Adapter : ShimeNmsAdapter {
    @Volatile
    private var currentStatus = ShimeNmsAdapterStatus.CREATED

    override val adapterId: String = "paper-1.20.6"

    override val supportedMinecraftVersions: Set<ShimeMinecraftVersion> =
        setOf(ShimeMinecraftVersion.PAPER_1_20_6)

    override fun supports(feature: ShimeNmsFeature): Boolean {
        return currentStatus == ShimeNmsAdapterStatus.COMPATIBLE && feature == ShimeNmsFeature.VERSION_DETECTION
    }

    override fun initialize(context: ShimeNmsAdapterContext) {
        currentStatus = ShimeNmsAdapterStatus.INITIALIZING
        val minecraft = context.version.minecraftVersion
        if (minecraft == null || minecraft !in supportedMinecraftVersions) {
            currentStatus = ShimeNmsAdapterStatus.UNSUPPORTED_VERSION
            context.logger.warn(
                ShimeLogCategory.NMS,
                "Built-in adapter '$adapterId' does not support the detected version " +
                    "(${minecraft ?: "unknown"}); only version detection remains available."
            )
            return
        }
        currentStatus = ShimeNmsAdapterStatus.COMPATIBLE
        context.logger.debug(
            ShimeLogCategory.NMS,
            "Built-in adapter '$adapterId' is compatible with Minecraft $minecraft; " +
                "no internal NMS features are enabled."
        )
    }

    override fun shutdown() {
        currentStatus = ShimeNmsAdapterStatus.CLOSED
    }

    override fun status(): ShimeNmsAdapterStatus = currentStatus
}
