package shimeserver.shimepluginlibrary.shimenms

/**
 * NMS アダプターが実装できる任意の機能。
 *
 * 組み込みアダプターは [VERSION_DETECTION] のみを報告する。サーバー内部を利用するアダプターは、
 * サポート対象のサーバーバージョンについて検証済みの機能だけを正確に宣言しなければならない。
 * マネージャーは、サーバーバージョンが互換性ありに見えるという理由だけで機能を仮定することはない。
 */
enum class ShimeNmsFeature(val label: String) {
    VERSION_DETECTION("version detection"),
    PACKET_SEND("packet sending"),
    PACKET_RECEIVE("packet receiving"),
    CUSTOM_ENTITY("custom entities"),
    VIRTUAL_ENTITY("virtual entities"),
    CUSTOM_METADATA("custom metadata"),
    PLAYER_VIEW_CONTROL("per-player view control"),
    CUSTOM_NAME_TAG("custom name tags"),
    CUSTOM_EQUIPMENT("custom equipment"),
    CUSTOM_ANIMATION("custom animations"),
    ENTITY_VISIBILITY("entity visibility"),
    PACKET_FILTER("packet filters"),
    PACKET_MONITOR("packet monitoring"),
    BENCHMARK("NMS benchmarks")
}
