package shimeserver.shimepluginlibrary.shimenms

/**
 * Paper 1.20.6 向けの組み込みアダプター。
 *
 * 意図的にサーバー内部を使用しない。検出されたバージョンが 1.20.6 であることを確認し、
 * [ShimeNmsFeature.VERSION_DETECTION] のみを報告する。パケット、エンティティ、表示に関する機能は、
 * 別途検証されたアダプターが登録するまで利用できないままである。
 */
class ShimePaper1206Adapter : ShimeBuiltInPaperAdapter(
    adapterId = "paper-1.20.6",
    supportedMinecraftVersions = setOf(ShimeMinecraftVersion.PAPER_1_20_6)
)
