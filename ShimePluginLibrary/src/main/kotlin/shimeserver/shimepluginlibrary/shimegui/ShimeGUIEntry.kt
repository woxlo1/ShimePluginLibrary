package shimeserver.shimepluginlibrary.shimegui

import org.bukkit.inventory.ItemStack

/**
 * 検索・並べ替え・絞り込みに対応した一覧 GUI の 1 行分。
 *
 * @property key 一意な識別子。操作履歴や選択結果で使う。
 * @property item 表示するアイテム。
 * @property searchText 検索対象の文字列。大文字小文字は区別しない。
 * @property sortKey 並べ替えに使う値。null の場合は [key] を使う。
 * @property group 絞り込みに使うグループ名。
 */
data class ShimeGUIEntry(
    val key: String,
    val item: ItemStack?,
    val searchText: String = key,
    val sortKey: String? = null,
    val group: String? = null
) {
    init {
        require(key.isNotBlank()) { "GUI entry key must not be blank." }
    }

    /** 実際の並べ替えキー。 */
    fun effectiveSortKey(): String = sortKey ?: key
}

/** 一覧 GUI の並べ替え方向。 */
enum class ShimeGUISortOrder {
    ASCENDING,
    DESCENDING
}

/** 一覧 GUI の絞り込み条件。 */
fun interface ShimeGUIEntryFilter {
    fun test(entry: ShimeGUIEntry): Boolean
}
