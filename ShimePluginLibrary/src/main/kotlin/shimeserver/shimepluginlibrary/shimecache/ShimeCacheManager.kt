package shimeserver.shimepluginlibrary.shimecache

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.concurrent.ConcurrentHashMap

/** 名前付きキャッシュのレジストリ。 */
class ShimeCacheManager : ShimeAbstractService(SERVICE_NAME) {
    private val caches = ConcurrentHashMap<String, ShimeCache<*, *>>()

    override fun onInitialize(context: ShimeContext) {
        // キャッシュは機能マネージャーによって必要時に作成される。
    }

    override fun onShutdown() {
        caches.values.forEach { it.clear() }
        caches.clear()
    }

    fun <K : Any, V : Any> create(
        name: String,
        maxSize: Int = 1_000,
        defaultTtlMillis: Long = 0L
    ): ShimeMemoryCache<K, V> {
        require(name.isNotBlank()) { "Cache name must not be blank." }
        val cache = ShimeMemoryCache<K, V>(maxSize, defaultTtlMillis)
        register(name, cache)
        return cache
    }

    fun <K : Any, V : Any> createLoading(
        name: String,
        maxSize: Int = 1_000,
        defaultTtlMillis: Long = 0L,
        loader: (K) -> V?
    ): ShimeLoadingCache<K, V> {
        val cache = ShimeLoadingCache(create<K, V>(name, maxSize, defaultTtlMillis), loader)
        return cache
    }

    fun register(name: String, cache: ShimeCache<*, *>, replace: Boolean = false) {
        require(name.isNotBlank()) { "Cache name must not be blank." }
        val previous = caches.put(name, cache)
        if (previous != null && !replace) {
            caches[name] = previous
            throw IllegalArgumentException("Cache '$name' is already registered.")
        }
    }

    fun get(name: String): ShimeCache<*, *>? = caches[name]

    @Suppress("UNCHECKED_CAST")
    fun <K : Any, V : Any> require(name: String): ShimeCache<K, V> {
        return (get(name) ?: throw IllegalStateException("Cache '$name' is not registered.")) as ShimeCache<K, V>
    }

    fun remove(name: String): Boolean = caches.remove(name) != null

    fun stats(): Map<String, ShimeCacheStats> = caches.mapValues { (_, cache) -> cache.stats() }

    companion object {
        const val SERVICE_NAME = "cache"
    }
}
