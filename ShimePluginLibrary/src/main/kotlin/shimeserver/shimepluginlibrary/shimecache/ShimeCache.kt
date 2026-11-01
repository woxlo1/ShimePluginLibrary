package shimeserver.shimepluginlibrary.shimecache

/** エントリごとの有効期限を任意で設定できる、スレッドセーフなキー/バリューキャッシュ。 */
interface ShimeCache<K : Any, V : Any> {
    fun get(key: K): V?

    fun put(key: K, value: V, ttlMillis: Long? = null): V?

    fun remove(key: K): V?

    fun contains(key: K): Boolean

    fun clear()

    fun size(): Int

    fun keys(): Set<K>

    fun stats(): ShimeCacheStats
}
