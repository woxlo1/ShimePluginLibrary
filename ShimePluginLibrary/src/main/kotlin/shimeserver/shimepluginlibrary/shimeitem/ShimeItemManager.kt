package shimeserver.shimepluginlibrary.shimeitem

import org.bukkit.Bukkit
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
import java.util.concurrent.atomic.AtomicLong

/**
 * カスタムアイテムとそのインタラクションハンドラーのレジストリ。
 *
 * クールダウンはプレイヤーと定義ごとに管理されます。マネージャーは定義のハンドラーがクリックを処理したと
 * 報告した場合にのみ、そのインタラクションをキャンセルします。使用回数、最終使用時刻、プレイヤーごとの
 * 有効・無効設定、所有者、取引可否、インスタンス ID も管理します。
 */
class ShimeItemManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger,
    legacyPlugin: JavaPlugin? = null
) : ShimeAbstractService(SERVICE_NAME), Listener {
    private val definitions = ConcurrentHashMap<String, ShimeItemDefinition>()
    private val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val usageCounts = ConcurrentHashMap<String, AtomicLong>()
    private val playerUsage = ConcurrentHashMap<UUID, ConcurrentHashMap<String, AtomicLong>>()
    private val lastUsed = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val disabled = ConcurrentHashMap<UUID, MutableSet<String>>()
    private val identifier = ShimeItemIdentifier(plugin, legacyPlugin)

    override fun onInitialize(context: ShimeContext) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onShutdown() {
        HandlerList.unregisterAll(this)
        definitions.clear()
        cooldowns.clear()
        usageCounts.clear()
        playerUsage.clear()
        lastUsed.clear()
        disabled.clear()
    }

    /**
     * 定義を登録する。
     *
     * @throws IllegalArgumentException ID が空、または既に登録済みで [replace] が false の場合。
     */
    fun register(definition: ShimeItemDefinition, replace: Boolean = false) {
        ShimeValidation.requireSafeKey(definition.id, "item definition id")
        if (replace) {
            definitions[definition.id] = definition
            return
        }
        if (definitions.putIfAbsent(definition.id, definition) != null) {
            throw IllegalArgumentException("Item definition '${definition.id}' is already registered.")
        }
    }

    /**
     * テンプレートから定義を登録する。
     *
     * 生成されるアイテムには [ShimeItemManager.create] が定義バージョンとインスタンス ID を付与する。
     */
    @JvmOverloads
    fun registerTemplate(
        id: String,
        template: ShimeItemTemplate,
        version: Int = 1,
        cooldownMillis: Long = 0L,
        tradeable: Boolean = true,
        rightClickHandler: ((Player, ItemStack) -> Boolean)? = null,
        leftClickHandler: ((Player, ItemStack) -> Boolean)? = null,
        migration: ((ItemStack, Int) -> ItemStack)? = null,
        replace: Boolean = false
    ) {
        register(
            ShimeItemDefinition(
                id = id,
                provider = { template.build(plugin) },
                rightClickHandler = rightClickHandler,
                leftClickHandler = leftClickHandler,
                cooldownMillis = cooldownMillis,
                version = version,
                tradeable = tradeable,
                template = template,
                migration = migration
            ),
            replace
        )
    }

    /**
     * 複数のテンプレートをまとめて登録する。
     *
     * [replace] が false の場合、登録済みの ID が 1 つでも含まれていれば何も登録せずに例外をスローします。
     */
    @JvmOverloads
    fun registerTemplates(templates: Map<String, ShimeItemTemplate>, replace: Boolean = false) {
        if (!replace) {
            val duplicates = templates.keys.filter { definitions.containsKey(it) }
            if (duplicates.isNotEmpty()) {
                throw IllegalArgumentException(
                    "Item definitions are already registered: ${duplicates.sorted().joinToString()}."
                )
            }
        }
        templates.forEach { (id, template) -> registerTemplate(id, template, replace = replace) }
    }

    fun unregister(id: String): Boolean {
        val removed = definitions.remove(id) != null
        if (removed) {
            resetUsage(id)
        }
        return removed
    }

    fun get(id: String): ShimeItemDefinition? = definitions[id]

    fun ids(): Set<String> = definitions.keys.toSet()

    /** 登録済みの定義をすべて返す。 */
    fun registeredDefinitions(): Map<String, ShimeItemDefinition> = definitions.toMap()

    /**
     * 登録済みの定義からアイテムを生成する。
     *
     * @throws IllegalArgumentException 定義が存在しない場合。
     * @throws IllegalStateException プロバイダーが空気を返した場合。
     */
    fun create(id: String, player: Player? = null): ItemStack {
        val definition = get(id) ?: throw IllegalArgumentException("Item definition '$id' is not registered.")
        val item = definition.provider(player)
        if (item.type == Material.AIR) {
            throw IllegalStateException("Item definition '$id' produced air.")
        }
        return identifier.tag(item, id, definition.version)
    }

    fun identify(item: ItemStack?): ShimeItemDefinition? {
        val id = identifier.idOf(item) ?: return null
        return definitions[id]
    }

    fun idOf(item: ItemStack?): String? = identifier.idOf(item)

    fun isCustom(item: ItemStack?): Boolean = identifier.isCustom(item)

    /** アイテムに記録された定義バージョン。カスタムアイテムでない場合は 0。 */
    fun versionOf(item: ItemStack?): Int = identifier.versionOf(item)

    /**
     * アイテムが古い定義バージョンの場合に移行処理を適用する。
     *
     * 移行後のバージョンは現在の定義バージョンで上書きされますが、インスタンス ID は保持されます。
     * 移行処理が登録されていない場合でも、バージョンだけが現在の値へ更新されます。
     */
    fun migrate(item: ItemStack): ItemStack {
        val id = identifier.idOf(item) ?: return item
        val definition = definitions[id] ?: return item
        val currentVersion = identifier.versionOf(item)
        if (currentVersion >= definition.version) {
            return item
        }
        val migrated = definition.migrate(item, currentVersion)
        return identifier.tag(migrated, id, definition.version, newInstance = false)
    }

    fun setCooldown(player: Player, id: String, durationMillis: Long) {
        require(durationMillis >= 0) { "Cooldown must not be negative." }
        if (durationMillis == 0L) {
            clearCooldown(player, id)
            return
        }
        cooldowns.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }[id] =
            System.currentTimeMillis() + durationMillis
    }

    fun clearCooldown(player: Player, id: String) {
        cooldowns[player.uniqueId]?.remove(id)
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

    /** 定義全体の使用回数。 */
    fun usageCount(id: String): Long = usageCounts[id]?.get() ?: 0L

    /** プレイヤーごとの使用回数。 */
    fun usageCount(player: Player, id: String): Long = playerUsage[player.uniqueId]?.get(id)?.get() ?: 0L

    /** 定義ごとの使用回数のスナップショット。 */
    fun usageCounts(): Map<String, Long> = usageCounts.mapValues { it.value.get() }

    /** プレイヤーが最後にアイテムを使用した時刻。未使用の場合は null。 */
    fun lastUsedMillis(player: Player, id: String): Long? = lastUsed[player.uniqueId]?.get(id)

    /** 指定した定義の使用回数をリセットする。 */
    fun resetUsage(id: String) {
        usageCounts.remove(id)
        playerUsage.values.forEach { usage -> usage.remove(id) }
        lastUsed.values.forEach { used -> used.remove(id) }
    }

    /** プレイヤーに対して定義を有効または無効にする。 */
    fun setEnabled(player: Player, id: String, enabled: Boolean) {
        val set = disabled.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet<String>() }
        if (enabled) {
            set.remove(id)
        } else {
            set.add(id)
        }
    }

    /** プレイヤーに対して定義が有効な場合に true。既定ではすべて有効。 */
    fun isEnabled(player: Player, id: String): Boolean = disabled[player.uniqueId]?.contains(id) != true

    /** プレイヤーに対して無効化されている定義 ID。 */
    fun disabledIds(player: Player): Set<String> = disabled[player.uniqueId]?.toSet() ?: emptySet()

    /** アイテムの所有者を設定する。null を渡すと所有者を削除する。 */
    fun setOwner(item: ItemStack, owner: UUID?): ItemStack = identifier.setOwner(item, owner)

    /** アイテムの所有者。未設定の場合は null。 */
    fun ownerOf(item: ItemStack?): UUID? = identifier.ownerOf(item)

    /** プレイヤーがアイテムの所有者である場合に true。 */
    fun isOwner(item: ItemStack?, player: Player): Boolean = ownerOf(item) == player.uniqueId

    /**
     * アイテムを [actor] が取引できるかを判定する。
     *
     * カスタムアイテムでない場合は常に true。カスタムアイテムの場合は定義の [ShimeItemDefinition.tradeable] が
     * true である必要があり、所有者が設定されている場合は所有者本人だけが取引できます。このメソッドは判定のみを
     * 行うため、実際の取引処理から呼び出してください。
     */
    fun canTrade(item: ItemStack?, actor: UUID?): Boolean {
        val definition = identify(item) ?: return true
        if (!definition.tradeable) {
            return false
        }
        val owner = ownerOf(item) ?: return true
        return owner == actor
    }

    /** 生成時に付与されたインスタンス ID。 */
    fun instanceIdOf(item: ItemStack?): UUID? = identifier.instanceIdOf(item)

    /**
     * 2 つのアイテムが同じインスタンス ID を共有している場合に true。
     *
     * 複製されたアイテムは同じ ID を持つため、重複生成の検出に利用できます。どちらかに ID がない場合は false。
     */
    fun isSameInstance(first: ItemStack?, second: ItemStack?): Boolean {
        val id = instanceIdOf(first) ?: return false
        return id == instanceIdOf(second)
    }

    /** アイテムの構造を検証する。 */
    fun validate(item: ItemStack): ShimeItemValidation {
        val issues = ArrayList<String>()
        if (item.type.isAir) {
            issues += "Item material must not be air."
        }
        if (item.amount < 1) {
            issues += "Item amount must be positive."
        } else if (item.amount > item.maxStackSize) {
            issues += "Item amount ${item.amount} exceeds the maximum stack size ${item.maxStackSize}."
        }
        val id = identifier.idOf(item)
        if (id != null) {
            if (id.isBlank()) {
                issues += "Custom item id must not be blank."
            }
            if (identifier.versionOf(item) < 1) {
                issues += "Custom item version must be at least 1."
            }
            if (identifier.instanceIdOf(item) == null) {
                issues += "Custom item is missing a valid instance id."
            }
            if (id.isNotBlank() && !definitions.containsKey(id)) {
                issues += "Custom item id '$id' is not registered."
            }
        }
        return ShimeItemValidation(issues.isEmpty(), issues)
    }

    /**
     * アイテムをプレイヤーへ安全に配布する。
     *
     * 格納できなかった分はプレイヤーの位置にドロップします。プライマリスレッド以外から呼び出すと
     * [IllegalStateException] をスローします。
     */
    fun give(player: Player, item: ItemStack): ShimeItemDistribution {
        if (!plugin.server.isPrimaryThread) {
            throw IllegalStateException("Items can only be distributed from the primary thread.")
        }
        val requested = item.amount
        if (requested <= 0) {
            return ShimeItemDistribution(0, 0, 0)
        }
        val leftovers = player.inventory.addItem(item.clone())
        var dropped = 0
        leftovers.values.forEach { leftover ->
            dropped += leftover.amount
            player.world.dropItemNaturally(player.location, leftover)
        }
        return ShimeItemDistribution(requested, requested - dropped, dropped)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            return
        }
        val player = event.player
        val item = player.inventory.itemInMainHand
        val definition = identify(item) ?: return
        if (!isEnabled(player, definition.id)) {
            return
        }
        val click = when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> ShimeItemClick.RIGHT
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> ShimeItemClick.LEFT
            else -> return
        }
        val handler = when (click) {
            ShimeItemClick.RIGHT -> definition.rightClickHandler
            ShimeItemClick.LEFT -> definition.leftClickHandler
        } ?: return

        val useEvent = ShimeItemUseEvent(player, item, definition, click)
        try {
            Bukkit.getPluginManager().callEvent(useEvent)
        } catch (throwable: Throwable) {
            logger.error(ShimeLogCategory.ITEM, "Item use event listener failed for '${definition.id}'.", throwable)
            return
        }
        if (useEvent.isCancelled) {
            event.isCancelled = true
            return
        }

        val remaining = remainingCooldown(player, definition.id)
        if (remaining > 0) {
            event.isCancelled = true
            return
        }
        try {
            val handled = handler(player, item)
            if (handled) {
                event.isCancelled = true
                recordUsage(player, definition.id)
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
        val uuid = event.player.uniqueId
        cooldowns.remove(uuid)
        playerUsage.remove(uuid)
        lastUsed.remove(uuid)
        disabled.remove(uuid)
    }

    private fun recordUsage(player: Player, id: String) {
        usageCounts.computeIfAbsent(id) { AtomicLong() }.incrementAndGet()
        playerUsage.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
            .computeIfAbsent(id) { AtomicLong() }
            .incrementAndGet()
        lastUsed.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }[id] = System.currentTimeMillis()
    }

    companion object {
        const val SERVICE_NAME = "item"
    }
}
