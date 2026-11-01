package shimeserver.shimepluginlibrary.shimenms

/**
 * 診断および管理コマンド向けの NMS 可用性のスナップショット。
 *
 * 互換性のあるアダプターでも、バージョンのみを検証する場合は空の [supportedFeatures] 集合を
 * 公開することがある。[packetTransportAvailable] は別途報告される。特定の機能が未サポートでも
 * トランスポートが存在することがあるためである。
 */
class ShimeNmsReport(
    val version: ShimeNmsVersion,
    val adapterId: String?,
    val adapterStatus: ShimeNmsAdapterStatus?,
    val supportedFeatures: Set<ShimeNmsFeature>,
    val packetTransportAvailable: Boolean,
    val warnings: List<String>
) {
    /** 互換性のあるアダプターが稼働しているかどうかを返す。 */
    val isAvailable: Boolean
        get() = adapterStatus == ShimeNmsAdapterStatus.COMPATIBLE

    /** [feature] がこのスナップショットで利用可能かどうかを返す。 */
    fun supports(feature: ShimeNmsFeature): Boolean = feature in supportedFeatures

    /** コマンド応答やログエントリに適した複数行のレポート。 */
    fun summary(): String {
        val builder = StringBuilder()
        builder.append("NMS version: ").append(version.summary()).append('\n')
        builder.append("Adapter: ").append(adapterId ?: "none")
        builder.append(" (").append(adapterStatus ?: "not initialized").append(")\n")
        builder.append("Packet transport: ").append(if (packetTransportAvailable) "available" else "unavailable").append('\n')
        builder.append("Features: ")
        builder.append(
            if (supportedFeatures.isEmpty()) {
                "none"
            } else {
                supportedFeatures.sortedBy { it.ordinal }.joinToString { it.label }
            }
        )
        if (warnings.isNotEmpty()) {
            builder.append('\n').append("Warnings:")
            warnings.forEach { builder.append('\n').append("- ").append(it) }
        }
        return builder.toString()
    }

    override fun toString(): String = summary()
}
