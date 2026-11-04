package shimeserver.shimepluginlibrary.shimenms

import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory

/**
 * Paper 向け組み込みアダプターの共通ライフサイクル実装。
 *
 * 意図的にサーバー内部を使用しない。検出されたバージョンが対象集合に含まれることを確認し、
 * [ShimeNmsFeature.VERSION_DETECTION] のみを公開する。パケット、エンティティ、表示に関する機能は、
 * 別途検証されたアダプターが登録するまで利用できないままである。
 *
 * 状態は volatile に保持され、[initialize] と [shutdown] は同期化される。初期化の完了後に
 * [supports] を任意のスレッドから呼び出してよい。[shutdown] は冪等であり、停止後の再初期化は行わない。
 *
 * @param adapterId ログと診断に使用される安定した識別子。
 * @param supportedMinecraftVersions このアダプターが対象とする Minecraft バージョン。
 *   集合は防御的にコピーされる。
 * @throws IllegalArgumentException [adapterId] が空白、または [supportedMinecraftVersions] が空の場合。
 */
abstract class ShimeBuiltInPaperAdapter(
    override val adapterId: String,
    supportedMinecraftVersions: Set<ShimeMinecraftVersion>
) : ShimeNmsAdapter {
    init {
        require(adapterId.isNotBlank()) { "NMS adapter id must not be blank." }
        require(supportedMinecraftVersions.isNotEmpty()) {
            "An NMS adapter must declare at least one supported Minecraft version."
        }
    }

    /** 防御的にコピーされた、このアダプターの対象バージョン。 */
    override val supportedMinecraftVersions: Set<ShimeMinecraftVersion> =
        supportedMinecraftVersions.toSet()

    @Volatile
    private var currentStatus = ShimeNmsAdapterStatus.CREATED

    /**
     * このアダプターは [ShimeNmsFeature.VERSION_DETECTION] のみを実装する。
     *
     * 初期化が完了して互換状態になるまでは、どの機能も利用可能と報告しない。
     */
    override fun supports(feature: ShimeNmsFeature): Boolean {
        return currentStatus == ShimeNmsAdapterStatus.COMPATIBLE && feature == ShimeNmsFeature.VERSION_DETECTION
    }

    /**
     * 検出されたバージョンを検証し、互換状態へ遷移する。
     *
     * すでに互換状態または停止済みの場合は何もしない。バージョン不一致は例外ではなく
     * [ShimeNmsAdapterStatus.UNSUPPORTED_VERSION] として報告し、マネージャーが別のアダプターを
     * 試せるようにする。
     */
    @Synchronized
    override fun initialize(context: ShimeNmsAdapterContext) {
        if (currentStatus == ShimeNmsAdapterStatus.COMPATIBLE || currentStatus == ShimeNmsAdapterStatus.CLOSED) {
            return
        }
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

    /** アダプターを停止する。複数回呼び出しても安全である。 */
    @Synchronized
    override fun shutdown() {
        currentStatus = ShimeNmsAdapterStatus.CLOSED
    }

    /** 現在のアダプターステータスを返す。 */
    override fun status(): ShimeNmsAdapterStatus = currentStatus
}
