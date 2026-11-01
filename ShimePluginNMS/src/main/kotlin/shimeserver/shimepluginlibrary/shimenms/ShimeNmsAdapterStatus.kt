package shimeserver.shimepluginlibrary.shimenms

/**
 * NMS アダプターのライフサイクルと互換性の状態。
 *
 * [COMPATIBLE] は、アダプターが検出されたサーバーバージョン向けの初期化を完了したことを意味する。
 * すべての機能が実装されていることを意味するわけではない。その問いには [ShimeNmsAdapter.supports] を使う。
 */
enum class ShimeNmsAdapterStatus {
    /** まだ初期化されていない。 */
    CREATED,

    /** 初期化の進行中。 */
    INITIALIZING,

    /** 初期化済みで、検出されたサーバーバージョンと互換性がある。 */
    COMPATIBLE,

    /** アダプターはロードされたが、検出されたサーバーバージョンをサポートしていない。 */
    UNSUPPORTED_VERSION,

    /** 初期化に失敗した。アダプターは使用できない。 */
    FAILED,

    /** アダプターはシャットダウンされた。 */
    CLOSED
}
