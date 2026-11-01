package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 宣言的なカスタムアイテム定義。
 *
 * 新しいアイテムの生成はプロバイダーが担当します。マネージャーはプロバイダーが返した後に識別タグを付与するため、
 * プロバイダーが誤ってタグなしの複製を作成することはありません。
 */
class ShimeItemDefinition(
    val id: String,
    val provider: (Player?) -> ItemStack,
    val rightClickHandler: ((Player, ItemStack) -> Boolean)? = null,
    val leftClickHandler: ((Player, ItemStack) -> Boolean)? = null,
    val cooldownMillis: Long = 0L
) {
    init {
        require(id.isNotBlank()) { "Item definition id must not be blank." }
        require(cooldownMillis >= 0) { "Item cooldown must not be negative." }
    }
}
