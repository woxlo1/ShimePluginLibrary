package shimeserver.shimepluginlibrary.shimepacket

/**
 * プラットフォーム非依存のパケット記述。
 *
 * アダプターはネイティブのパケットオブジェクトをこのインターフェースの実装でラップする。ライブラリは
 * 公開 API でサーバー内部のパケット型を公開しないため、ライブラリに対してコンパイルされたプラグインは、
 * サーバー実装が変わっても動作し続ける。
 */
interface ShimePacket {
    /** ログやフィルターで使用される、安定した人間が読める形式のパケット名。 */
    val name: String

    /** このパケットが進む方向。 */
    val direction: ShimePacketDirection
}
