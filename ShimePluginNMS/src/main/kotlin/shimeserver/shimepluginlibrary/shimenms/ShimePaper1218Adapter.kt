package shimeserver.shimepluginlibrary.shimenms

/**
 * Paper 1.21.8 向けの組み込みアダプター。
 *
 * 意図的にサーバー内部を使用しない。検出されたバージョンが 1.21.8 であることを確認し、
 * [ShimeNmsFeature.VERSION_DETECTION] のみを報告する。1.21.8 の内部クラスやパケット形式は
 * バージョン間で変化するため、検証済みのトランスポートを提供する外部アダプターが登録されるまで、
 * パケット、エンティティ、表示に関する機能は利用できない。
 */
class ShimePaper1218Adapter : ShimeBuiltInPaperAdapter(
    adapterId = "paper-1.21.8",
    supportedMinecraftVersions = setOf(ShimeMinecraftVersion.PAPER_1_21_8)
)
