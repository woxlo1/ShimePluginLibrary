package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 宣言的なカスタムアイテム定義。
 *
 * 新しいアイテムの生成はプロバイダーが担当します。マネージャーはプロバイダーが返した後に識別タグを付与するため、
 * プロバイダーが誤ってタグなしの複製を作成することはありません。
 *
 * @param version 定義のバージョン。既存アイテムの移行判定に使用する。
 * @param tradeable false の場合、[ShimeItemManager.canTrade] は常に false を返す。
 * @param migration 古いバージョンのアイテムを現在の形式へ変換する処理。第 2 引数は検出された旧バージョン。
 */
class ShimeItemDefinition(
    val id: String,
    val provider: (Player?) -> ItemStack,
    val rightClickHandler: ((Player, ItemStack) -> Boolean)? = null,
    val leftClickHandler: ((Player, ItemStack) -> Boolean)? = null,
    val cooldownMillis: Long = 0L,
    val version: Int = 1,
    val tradeable: Boolean = true,
    val template: ShimeItemTemplate? = null,
    val migration: ((ItemStack, Int) -> ItemStack)? = null
) {
    init {
        require(id.isNotBlank()) { "Item definition id must not be blank." }
        require(cooldownMillis >= 0) { "Item cooldown must not be negative." }
        require(version >= 1) { "Item definition version must be at least 1." }
    }

    /** 古いバージョンのアイテムへ移行処理を適用する。移行処理がない場合は入力をそのまま返す。 */
    fun migrate(item: ItemStack, fromVersion: Int): ItemStack {
        if (fromVersion >= version) {
            return item
        }
        return migration?.invoke(item, fromVersion) ?: item
    }
}
