package shimeserver.shimepluginlibrary.shimegui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 検索・グループ絞り込み・並べ替え・ページ送りに対応した一覧 GUI。
 *
 * 表示データは [entries] から取得します。状態はプレイヤーごとに保持され、[handleClose] で破棄されます。
 * データの取得を非同期で行う場合は、あらかじめ [ShimeGUIManager.updateAfter] でキャッシュを更新してください。
 */
abstract class ShimeListGUI(
    id: String,
    title: Component,
    size: Int = 54
) : ShimePagedGUI(id, title, size) {
    private data class ListState(
        val query: String = "",
        val group: String? = null,
        val order: ShimeGUISortOrder = ShimeGUISortOrder.ASCENDING,
        val filter: ShimeGUIEntryFilter? = null
    )

    private val states = ConcurrentHashMap<UUID, ListState>()

    init {
        require(size >= 18) { "A list GUI requires at least one content row and one navigation row." }
    }

    /** 一覧に表示する候補を返す。 */
    abstract fun entries(player: Player): List<ShimeGUIEntry>

    /** 一覧の項目が選択されたときに呼ばれる。 */
    protected open fun onSelect(player: Player, entry: ShimeGUIEntry, clickType: ClickType) {
        // サブクラスはここで画面遷移やアクションを実行します。
    }

    /** 現在の検索文字列を返す。 */
    fun searchQuery(player: Player): String = state(player).query

    /** 現在のグループ絞り込みを返す。 */
    fun groupFilter(player: Player): String? = state(player).group

    /** 現在の並べ替え方向を返す。 */
    fun sortOrder(player: Player): ShimeGUISortOrder = state(player).order

    /** 検索文字列を設定する。 */
    fun setSearch(player: Player, query: String) {
        states[player.uniqueId] = state(player).copy(query = query.trim())
    }

    /** グループ絞り込みを設定する。null は絞り込みなし。 */
    fun setGroupFilter(player: Player, group: String?) {
        states[player.uniqueId] = state(player).copy(group = group?.takeIf { it.isNotBlank() })
    }

    /** 並べ替え方向を設定する。 */
    fun setSortOrder(player: Player, order: ShimeGUISortOrder) {
        states[player.uniqueId] = state(player).copy(order = order)
    }

    /** 追加の絞り込み条件を設定する。null は解除。 */
    fun setFilter(player: Player, filter: ShimeGUIEntryFilter?) {
        states[player.uniqueId] = state(player).copy(filter = filter)
    }

    /** 検索・絞り込み・並べ替えの状態を初期化する。 */
    fun resetState(player: Player) {
        states.remove(player.uniqueId)
    }

    /** 現在の条件で表示される項目を返す。 */
    fun visibleEntries(player: Player): List<ShimeGUIEntry> {
        val state = state(player)
        val filtered = entries(player).asSequence()
            .filter { entry ->
                state.query.isEmpty() ||
                    entry.searchText.contains(state.query, ignoreCase = true) ||
                    entry.key.contains(state.query, ignoreCase = true)
            }
            .filter { entry -> state.group == null || entry.group == state.group }
            .filter { entry -> state.filter?.test(entry) ?: true }
            .toList()
        val comparator = compareBy<ShimeGUIEntry> { it.effectiveSortKey() }
        return if (state.order == ShimeGUISortOrder.ASCENDING) filtered.sortedWith(comparator)
        else filtered.sortedWith(comparator.reversed())
    }

    /** グループ候補を返す。 */
    fun groups(player: Player): List<String> {
        return entries(player).mapNotNull { it.group }.distinct().sorted()
    }

    override fun pageCount(player: Player): Int {
        val count = visibleEntries(player).size
        return if (count == 0) 1 else (count + contentSlots - 1) / contentSlots
    }

    override fun renderPage(player: Player, inventory: Inventory, page: Int, pageCount: Int) {
        val visible = visibleEntries(player)
        val start = page * contentSlots
        for (offset in 0 until contentSlots) {
            val index = start + offset
            inventory.setItem(offset, visible.getOrNull(index)?.item)
        }
        if (visible.isEmpty()) {
            renderEmpty(player, inventory)
        }
    }

    /** 項目が 1 つもない場合の表示をカスタマイズするフック。 */
    protected open fun renderEmpty(player: Player, inventory: Inventory) {
        // サブクラスはここに「該当なし」のアイテムを配置できます。
    }

    override fun handleClick(
        player: Player,
        slot: Int,
        clickType: ClickType,
        event: InventoryClickEvent
    ): ShimeGUIAction {
        val navigation = super.handleClick(player, slot, clickType, event)
        if (navigation != ShimeGUIAction.NONE) {
            return navigation
        }
        if (slot !in 0 until contentSlots) {
            return ShimeGUIAction.NONE
        }
        val index = currentPage(player) * contentSlots + slot
        val entry = visibleEntries(player).getOrNull(index) ?: return ShimeGUIAction.NONE
        onSelect(player, entry, clickType)
        return ShimeGUIAction.NONE
    }

    override fun handleClose(player: Player) {
        super.handleClose(player)
        states.remove(player.uniqueId)
    }

    /** 1 ページに表示できる項目数。 */
    protected val contentSlots: Int
        get() = size - 9

    private fun state(player: Player): ListState = states[player.uniqueId] ?: ListState()
}
