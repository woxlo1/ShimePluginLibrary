package shimeserver.shimepluginlibrary.shimenms

import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * NMS アダプターのスレッドセーフなレジストリ。
 *
 * 登録順は保持される。呼び出し元が明示的にアダプターの置き換えを要求しない限り、重複した識別子は
 * 拒否される。これにより、プラグインが検証済みのアダプターを誤って黙って上書きすることを防ぐ。
 */
class ShimeNmsAdapterRegistry @JvmOverloads constructor(private val logger: ShimeLogger? = null) {
    private val lock = ReentrantLock()
    private val adapters = LinkedHashMap<String, ShimeNmsAdapter>()

    /**
     * [adapter] を登録する。
     *
     * @throws IllegalArgumentException 識別子が空白であるか、[replace] なしですでに登録されている場合。
     */
    @JvmOverloads
    fun register(adapter: ShimeNmsAdapter, replace: Boolean = false) {
        val id = adapter.adapterId
        require(id.isNotBlank()) { "NMS adapter id must not be blank." }
        lock.withLock {
            val existing = adapters[id]
            if (existing != null && !replace) {
                throw IllegalArgumentException("NMS adapter '$id' is already registered.")
            }
            adapters[id] = adapter
        }
        logger?.debug(ShimeLogCategory.NMS, "Registered NMS adapter '$id'.")
    }

    /** id でアダプターを削除する。存在していたかどうかを返す。 */
    fun unregister(id: String): Boolean {
        val removed = lock.withLock { adapters.remove(id) != null }
        if (removed) {
            logger?.debug(ShimeLogCategory.NMS, "Unregistered NMS adapter '$id'.")
        }
        return removed
    }

    /** id でアダプターを返す。存在しない場合は `null`。 */
    fun get(id: String): ShimeNmsAdapter? = lock.withLock { adapters[id] }

    /** 登録順で、登録済みアダプターのスナップショットを返す。 */
    fun adapters(): List<ShimeNmsAdapter> = lock.withLock { adapters.values.toList() }

    /** [version] のサポートを宣言しているアダプターを、登録順で返す。 */
    fun findCompatible(version: ShimeNmsVersion): List<ShimeNmsAdapter> {
        return adapters().filter { adapter ->
            try {
                adapter.supportsVersion(version)
            } catch (throwable: Throwable) {
                logger?.warn(
                    ShimeLogCategory.NMS,
                    "NMS adapter '${adapter.adapterId}' failed while checking version support.",
                    throwable
                )
                false
            }
        }
    }

    /** すべてのアダプターを削除する。 */
    fun clear() {
        lock.withLock { adapters.clear() }
    }
}
