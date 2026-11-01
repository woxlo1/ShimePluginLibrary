package shimeserver.shimepluginlibrary.shimeapi

/**
 * サービス、モジュール、その他の長寿命ライブラリオブジェクトで共有されるライフサイクル状態。
 *
 * enum の順序は意図的なものである。これにより診断や状態比較が読みやすくなる。
 */
enum class ShimeState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    STOPPING,
    STOPPED,
    FAILED;

    val isUsable: Boolean
        get() = this == READY

    val isTerminal: Boolean
        get() = this == STOPPED || this == FAILED
}
