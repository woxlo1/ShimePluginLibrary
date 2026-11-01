package shimeserver.shimepluginlibrary.shimescoreboard

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * 宣言的なサイドバー定義。
 *
 * プロバイダーはプライマリスレッド上で、更新間隔ごとに最大 1 回呼び出される。処理は軽量に保つこと。
 * データベースやネットワークの処理は別の場所で行い、ここではキャッシュされた結果を読むこと。
 */
class ShimeSidebar(
    val id: String,
    val titleProvider: (Player) -> Component,
    val lineProvider: (Player) -> List<Component>,
    val updateIntervalTicks: Long = 20L
) {
    init {
        require(id.isNotBlank()) { "Sidebar id must not be blank." }
        require(updateIntervalTicks > 0) { "Sidebar update interval must be positive." }
    }
}
