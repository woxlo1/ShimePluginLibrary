package shimeserver.shimepluginlibrary.shimegui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.Sound
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * チェスト GUI の基底クラス。
 *
 * 描画は常にプライマリスレッドで行われます。マネージャーは既定でアイテムの移動をキャンセルします。
 * 意図的に編集可能なインベントリとして動作させる GUI に限り、[allowItemPickup] をオーバーライドしてください。
 */
abstract class ShimeGUI(
    val id: String,
    val title: Component,
    val size: Int
) {
    private val accessList = ConcurrentHashMap.newKeySet<UUID>()
    private var owner: UUID? = null

    init {
        require(id.isNotBlank()) { "GUI id must not be blank." }
        require(size in 9..54 && size % 9 == 0) { "GUI size must be a multiple of 9 between 9 and 54." }
    }

    /** GUI の所有者を設定する。 */
    fun setOwner(player: Player) {
        owner = player.uniqueId
    }

    /** GUI の所有者を設定する。 */
    fun setOwner(uuid: UUID) {
        owner = uuid
    }

    /** GUI の所有者を取得する。 */
    fun getOwner(): UUID? = owner

    /** アクセス権を付与する。 */
    fun grantAccess(player: Player) {
        accessList.add(player.uniqueId)
    }

    /** アクセス権を付与する。 */
    fun grantAccess(uuid: UUID) {
        accessList.add(uuid)
    }

    /** アクセス権を剥奪する。 */
    fun revokeAccess(player: Player) {
        accessList.remove(player.uniqueId)
    }

    /** アクセス権を剥奪する。 */
    fun revokeAccess(uuid: UUID) {
        accessList.remove(uuid)
    }

    /** プレイヤーがこの GUI にアクセスできるかを判定する。 */
    fun hasAccess(player: Player): Boolean {
        val uuid = player.uniqueId
        return owner == uuid || accessList.contains(uuid) || canAccess(player)
    }

    /** カスタムアクセス制御を実装するためのフック。 */
    open fun canAccess(player: Player): Boolean = true

    /** クリック時に再生するサウンド。 */
    open fun clickSound(): Sound? = null

    /** クリック時に送信するメッセージ。 */
    open fun clickMessage(player: Player, slot: Int, clickType: ClickType): Component? = null

    open fun render(player: Player, inventory: Inventory) {
        // サブクラスはここにアイテムを配置します。
    }

    open fun handleClick(
        player: Player,
        slot: Int,
        clickType: ClickType,
        event: InventoryClickEvent
    ): ShimeGUIAction = ShimeGUIAction.NONE

    open fun handleClose(player: Player) {
        // サブクラスはここでセッションごとの状態を解放します。
    }

    open fun allowItemPickup(): Boolean = false

    open fun allowDrag(): Boolean = false

    open fun refreshOnOpen(): Boolean = true
}
