package shimeserver.shimepluginlibrary.shimegui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * 同じレイアウトの GUI を繰り返し生成するためのテンプレート。
 *
 * テンプレートは不変であり、[instantiate] のたびに独立した [ShimeGUI] を返します。
 * アイテムの配置は呼び出し時点のプレイヤーに依存してよいため、描画コールバックに委譲しています。
 */
class ShimeGUITemplate(
    val id: String,
    val title: Component,
    val size: Int = 27,
    private val renderer: (Player, Inventory, ShimeGUITemplateContext) -> Unit = { _, _, _ -> },
    private val clickHandler: (Player, Int, ClickType, InventoryClickEvent, ShimeGUITemplateContext) -> ShimeGUIAction =
        { _, _, _, _, _ -> ShimeGUIAction.NONE }
) {
    init {
        require(id.isNotBlank()) { "GUI template id must not be blank." }
        require(size in 9..54 && size % 9 == 0) { "GUI template size must be a multiple of 9 between 9 and 54." }
    }

    /** テンプレートから新しい GUI を生成する。 */
    fun instantiate(): ShimeGUI {
        val context = ShimeGUITemplateContext(id, size)
        return object : ShimeGUI(id, title, size) {
            override fun render(player: Player, inventory: Inventory) {
                renderer(player, inventory, context)
            }

            override fun handleClick(
                player: Player,
                slot: Int,
                clickType: ClickType,
                event: InventoryClickEvent
            ): ShimeGUIAction {
                return clickHandler(player, slot, clickType, event, context)
            }
        }
    }

    /** テンプレートから生成した GUI を [player] に開く。 */
    fun open(manager: ShimeGUIManager, player: Player) {
        manager.open(player, instantiate())
    }
}

/** テンプレートインスタンスに紐づく不変のメタデータ。 */
class ShimeGUITemplateContext internal constructor(
    val templateId: String,
    val size: Int
) {
    /** 境界スロットかどうかを判定する。 */
    fun isBorder(slot: Int): Boolean {
        val row = slot / 9
        val column = slot % 9
        return row == 0 || column == 0 || column == 8 || slot >= size - 9
    }
}

/** スロットにアイテムを配置する簡易 DSL。 */
class ShimeGUIBuilder internal constructor(
    private val id: String,
    private val title: Component,
    private val size: Int
) {
    private val items = LinkedHashMap<Int, ItemStack>()
    private val handlers = LinkedHashMap<Int, (Player, ClickType) -> ShimeGUIAction>()
    private var sound: org.bukkit.Sound? = null
    private var message: ((Player, Int, ClickType) -> Component?)? = null

    /** スロットにアイテムを配置する。 */
    fun slot(slot: Int, item: ItemStack?): ShimeGUIBuilder {
        require(slot in 0 until size) { "Slot $slot is outside the GUI size $size." }
        if (item == null) {
            items.remove(slot)
        } else {
            items[slot] = item
        }
        return this
    }

    /** 複数スロットに同じアイテムを配置する。 */
    fun slots(slots: Iterable<Int>, item: ItemStack?): ShimeGUIBuilder {
        slots.forEach { slot(it, item) }
        return this
    }

    /** 外周を埋める。 */
    fun border(item: ItemStack?): ShimeGUIBuilder {
        for (slot in 0 until size) {
            val row = slot / 9
            val column = slot % 9
            if (row == 0 || column == 0 || column == 8 || slot >= size - 9) {
                slot(slot, item)
            }
        }
        return this
    }

    /** スロットのクリックハンドラーを登録する。 */
    fun onClick(slot: Int, handler: (Player, ClickType) -> ShimeGUIAction): ShimeGUIBuilder {
        require(slot in 0 until size) { "Slot $slot is outside the GUI size $size." }
        handlers[slot] = handler
        return this
    }

    /** クリック時に再生するサウンドを設定する。 */
    fun sound(value: org.bukkit.Sound?): ShimeGUIBuilder {
        sound = value
        return this
    }

    /** クリック時に送信するメッセージを設定する。 */
    fun message(value: (Player, Int, ClickType) -> Component?): ShimeGUIBuilder {
        message = value
        return this
    }

    /** GUI を構築する。 */
    fun build(): ShimeGUI {
        val snapshotItems = LinkedHashMap(items)
        val snapshotHandlers = LinkedHashMap(handlers)
        val snapshotSound = sound
        val snapshotMessage = message
        return object : ShimeGUI(id, title, size) {
            override fun render(player: Player, inventory: Inventory) {
                snapshotItems.forEach { (slot, item) -> inventory.setItem(slot, item) }
            }

            override fun handleClick(
                player: Player,
                slot: Int,
                clickType: ClickType,
                event: InventoryClickEvent
            ): ShimeGUIAction {
                return snapshotHandlers[slot]?.invoke(player, clickType) ?: ShimeGUIAction.NONE
            }

            override fun clickSound(): org.bukkit.Sound? = snapshotSound

            override fun clickMessage(player: Player, slot: Int, clickType: ClickType): Component? {
                return snapshotMessage?.invoke(player, slot, clickType)
            }
        }
    }
}

/**
 * GUI を DSL で構築する。
 *
 * @param id GUI の識別子。マネージャーのセッション管理に使われる。
 * @param title GUI のタイトル。
 * @param size インベントリサイズ。9 の倍数で 9..54。
 */
fun shimeGUI(
    id: String,
    title: Component,
    size: Int = 27,
    block: ShimeGUIBuilder.() -> Unit
): ShimeGUI {
    return ShimeGUIBuilder(id, title, size).apply(block).build()
}
