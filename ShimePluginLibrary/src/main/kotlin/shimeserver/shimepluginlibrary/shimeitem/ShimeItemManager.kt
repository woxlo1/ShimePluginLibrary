package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * カスタムアイテムとそのインタラクションハンドラーのレジストリ。
 *
 * クールダウンはプレイヤーと定義ごとに管理されます。マネージャーは定義のハンドラーがクリックを処理したと
 * 報告した場合にのみ、そのインタラクションをキャンセルします。
 */
class ShimeItemManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger
) : ShimeAbstractService(SERVICE_NAME), Listener {
    private val definitions = ConcurrentHashMap<String, ShimeItemDefinition>()
    private val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val identifier = ShimeItemIdentifier(plugin)

    override fun onInitialize(context: ShimeContext) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onShutdown() {
        HandlerList.unregisterAll(this)
        definitions.clear()
        cooldowns.clear()
    }

    fun register(definition: ShimeItemDefinition, replace: Boolean = false) {
        ShimeValidation.requireSafeKey(definition.id, "item definition id")
        val previous = definitions.put(definition.id, definition)
        if (previous != null && !replace) {
            definitions[definition.id] = previous
            throw IllegalArgumentException("Item definition '${definition.id}' is already registered.")
        }
    }

    fun unregister(id: String): Boolean = definitions.remove(id) != null

    fun get(id: String): ShimeItemDefinition? = definitions[id]

    fun ids(): Set<String> = definitions.keys.toSet()

    fun create(id: String, player: Player? = null): ItemStack {
        val definition = get(id) ?: throw IllegalArgumentException("Item definition '$id' is not registered.")
        val item = definition.provider(player)
        if (item.type == Material.AIR) {
            throw IllegalStateException("Item definition '$id' produced air.")
        }
        return identifier.tag(item, id)
    }

    fun identify(item: ItemStack?): ShimeItemDefinition? {
        val id = identifier.idOf(item) ?: return null
        return definitions[id]
    }

    fun idOf(item: ItemStack?): String? = identifier.idOf(item)

    fun isCustom(item: ItemStack?): Boolean = identifier.isCustom(item)

    fun setCooldown(player: Player, id: String, durationMillis: Long) {
        require(durationMillis >= 0) { "Cooldown must not be negative." }
        if (durationMillis == 0L) {
            cooldowns[player.uniqueId]?.remove(id)
            return
        }
        cooldowns.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }[id] =
            System.currentTimeMillis() + durationMillis
    }

    fun remainingCooldown(player: Player, id: String): Long {
        val expiry = cooldowns[player.uniqueId]?.get(id) ?: return 0L
        val remaining = expiry - System.currentTimeMillis()
        if (remaining <= 0) {
            cooldowns[player.uniqueId]?.remove(id)
            return 0L
        }
        return remaining
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            return
        }
        val player = event.player
        val item = player.inventory.itemInMainHand
        val definition = identify(item) ?: return
        val action = event.action
        val handler = when {
            action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK -> definition.rightClickHandler
            action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK -> definition.leftClickHandler
            else -> null
        } ?: return
        val remaining = remainingCooldown(player, definition.id)
        if (remaining > 0) {
            event.isCancelled = true
            return
        }
        try {
            val handled = handler(player, item)
            if (handled) {
                event.isCancelled = true
                if (definition.cooldownMillis > 0) {
                    setCooldown(player, definition.id, definition.cooldownMillis)
                }
            }
        } catch (throwable: Throwable) {
            logger.error(ShimeLogCategory.ITEM, "Item handler '${definition.id}' failed for ${player.name}.", throwable)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cooldowns.remove(event.player.uniqueId)
    }

    companion object {
        const val SERVICE_NAME = "item"
    }
}
