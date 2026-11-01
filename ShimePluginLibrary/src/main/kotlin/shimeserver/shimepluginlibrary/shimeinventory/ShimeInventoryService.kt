package shimeserver.shimepluginlibrary.shimeinventory

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * インベントリ関連のヘルパーと、コンパクトでバージョン間の互換性に優れたアイテムシリアライゼーション形式。
 *
 * 実際の [Inventory] や [Player] に触れるすべてのメソッドはプライマリスレッドで実行する必要があります。
 * 違反した場合はインベントリの破損を避けるため、[IllegalStateException] をスローします。
 * シリアライゼーションには Paper の [ItemStack] バイト形式を使用し、Java のオブジェクトシリアライゼーションを
 * 使わずにアイテムのメタデータを保持します。
 */
class ShimeInventoryService(
    private val scheduler: ShimeScheduler
) : ShimeAbstractService(SERVICE_NAME) {
    override fun onInitialize(context: ShimeContext) {
        // ステートレスなサービス。
    }

    /** すべてのスロットの防御的コピーを返します。空のスロットは null として保持されます。 */
    fun snapshot(inventory: Inventory): Array<ItemStack?> {
        requirePrimaryThread("snapshot an inventory")
        return Array(inventory.size) { index -> inventory.getItem(index)?.clone() }
    }

    /** インベントリを Base64 文字列にシリアライズします。 */
    fun serialize(inventory: Inventory): String {
        requirePrimaryThread("serialize an inventory")
        return serialize(snapshot(inventory))
    }

    /** アイテムスタックを Base64 文字列にシリアライズします。 */
    fun serialize(contents: Array<ItemStack?>): String {
        require(contents.size <= MAX_SLOTS) { "Inventory contains more than $MAX_SLOTS slots." }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(contents.size)
            contents.forEach { item ->
                if (item == null || item.type.isAir) {
                    output.writeBoolean(false)
                } else {
                    val itemBytes = item.serializeAsBytes()
                    require(itemBytes.size <= MAX_ITEM_BYTES) { "Serialized item exceeds $MAX_ITEM_BYTES bytes." }
                    output.writeBoolean(true)
                    output.writeInt(itemBytes.size)
                    output.write(itemBytes)
                }
            }
        }
        val encoded = Base64.getEncoder().encodeToString(bytes.toByteArray())
        require(encoded.length <= MAX_ENCODED_LENGTH) { "Encoded inventory exceeds the safety limit." }
        return encoded
    }

    /** [serialize] が生成した値をデシリアライズします。 */
    fun deserialize(encoded: String): Array<ItemStack?> {
        require(encoded.length <= MAX_ENCODED_LENGTH) { "Encoded inventory exceeds the safety limit." }
        val decoded = try {
            Base64.getDecoder().decode(encoded)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Inventory data is not valid Base64.", exception)
        }
        DataInputStream(ByteArrayInputStream(decoded)).use { input ->
            val size = input.readInt()
            require(size in 0..MAX_SLOTS) { "Inventory data declares an invalid slot count: $size." }
            return Array(size) {
                if (!input.readBoolean()) {
                    null
                } else {
                    val length = input.readInt()
                    require(length in 1..MAX_ITEM_BYTES) { "Inventory item declares an invalid length: $length." }
                    val itemBytes = ByteArray(length)
                    input.readFully(itemBytes)
                    ItemStack.deserializeBytes(itemBytes)
                }
            }
        }
    }

    /** [inventory] の内容を [serialize] が生成したデータで置き換えます。 */
    fun restore(inventory: Inventory, encoded: String) {
        requirePrimaryThread("restore an inventory")
        val contents = deserialize(encoded)
        require(contents.size == inventory.size) {
            "Serialized inventory has ${contents.size} slots but the target has ${inventory.size}."
        }
        contents.forEachIndexed { index, item -> inventory.setItem(index, item?.clone()) }
    }

    /** すべてのスロットが空の場合に true を返します。 */
    fun isEmpty(inventory: Inventory): Boolean {
        requirePrimaryThread("inspect an inventory")
        return inventory.contents.all { it == null || it.type.isAir }
    }

    /** スタック数を考慮したアイテムの総数を数えます。 */
    fun count(inventory: Inventory): Int {
        requirePrimaryThread("inspect an inventory")
        return inventory.contents.sumOf { item -> if (item == null || item.type.isAir) 0 else item.amount }
    }

    /** 何もドロップせずに [item] を追加できる場合に true を返します。 */
    fun hasSpace(inventory: Inventory, item: ItemStack): Boolean {
        requirePrimaryThread("inspect an inventory")
        val remaining = item.clone()
        inventory.storageContents.forEach { slot ->
            if (remaining.amount <= 0) {
                return true
            }
            if (slot == null || slot.type.isAir) {
                return true
            }
            if (slot.isSimilar(remaining)) {
                val capacity = slot.maxStackSize - slot.amount
                if (capacity > 0) {
                    remaining.amount -= capacity
                }
            }
        }
        return remaining.amount <= 0
    }

    /** プライマリスレッドでインベントリをクリアします。 */
    fun clear(inventory: Inventory) {
        requirePrimaryThread("clear an inventory")
        inventory.clear()
    }

    /**
     * アイテムをプレイヤーのインベントリに追加し、あふれた分をプレイヤーの位置にドロップします。
     * 格納できなかったアイテムの数を返します。
     */
    fun addOrDrop(player: Player, item: ItemStack): Int {
        requirePrimaryThread("add an item to a player inventory")
        val remaining = item.clone()
        val leftovers = player.inventory.addItem(remaining)
        var dropped = 0
        leftovers.values.forEach { leftover ->
            dropped += leftover.amount
            player.world.dropItemNaturally(player.location, leftover)
        }
        return dropped
    }

    private fun requirePrimaryThread(operation: String) {
        if (!scheduler.isPrimaryThread()) {
            throw IllegalStateException("Cannot $operation from a non-primary thread.")
        }
    }

    companion object {
        const val SERVICE_NAME = "inventory"
        private const val MAX_SLOTS = 4096
        private const val MAX_ITEM_BYTES = 1_048_576
        private const val MAX_ENCODED_LENGTH = 16_777_216
    }
}
