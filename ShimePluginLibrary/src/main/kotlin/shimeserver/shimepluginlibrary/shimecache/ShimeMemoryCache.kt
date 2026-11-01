package shimeserver.shimepluginlibrary.shimecache

import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * TTL をサポートする上限付き LRU キャッシュ。
 *
 * アクセス順序は単一のモニター下で [LinkedHashMap] によって維持される。このキャッシュは比較的小さな
 * ホットデータセット向けであり、データベースの結果セットは丸ごとキャッシュするのではなく
 * ページングすべきである。
 */
class ShimeMemoryCache<K : Any, V : Any>(
    private val maxSize: Int = 1_000,
    private val defaultTtlMillis: Long = 0L,
    private val timeSource: () -> Long = System::currentTimeMillis
) : ShimeCache<K, V> {
    init {
        require(maxSize > 0) { "Cache size must be greater than zero." }
        require(defaultTtlMillis >= 0) { "Default TTL must not be negative." }
    }

    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {}
    private val hits = AtomicLong()
    private val misses = AtomicLong()
    private val evictions = AtomicLong()
    private val expirations = AtomicLong()

    @Synchronized
    override fun get(key: K): V? {
        val entry = entries[key]
        if (entry == null) {
            misses.incrementAndGet()
            return null
        }
        if (isExpired(entry)) {
            entries.remove(key)
            expirations.incrementAndGet()
            misses.incrementAndGet()
            return null
        }
        hits.incrementAndGet()
        return entry.value
    }

    @Synchronized
    override fun put(key: K, value: V, ttlMillis: Long?): V? {
        val effectiveTtl = ttlMillis ?: defaultTtlMillis
        require(effectiveTtl >= 0) { "TTL must not be negative." }
        val expiresAt = if (effectiveTtl == 0L) Long.MAX_VALUE else timeSource() + effectiveTtl
        val previous = entries.put(key, Entry(value, expiresAt))
        trimToSize()
        return previous?.value
    }

    @Synchronized
    override fun remove(key: K): V? {
        return entries.remove(key)?.value
    }

    @Synchronized
    override fun contains(key: K): Boolean {
        val entry = entries[key] ?: return false
        if (isExpired(entry)) {
            entries.remove(key)
            expirations.incrementAndGet()
            return false
        }
        return true
    }

    @Synchronized
    override fun clear() {
        entries.clear()
    }

    @Synchronized
    override fun size(): Int {
        purgeExpired()
        return entries.size
    }

    @Synchronized
    override fun keys(): Set<K> {
        purgeExpired()
        return entries.keys.toSet()
    }

    override fun stats(): ShimeCacheStats = synchronized(this) {
        purgeExpired()
        ShimeCacheStats(
            hits = hits.get(),
            misses = misses.get(),
            evictions = evictions.get(),
            expirations = expirations.get(),
            size = entries.size,
            capacity = maxSize
        )
    }

    private fun trimToSize() {
        while (entries.size > maxSize) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) {
                break
            }
            iterator.next()
            iterator.remove()
            evictions.incrementAndGet()
        }
    }

    private fun purgeExpired() {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().value)) {
                iterator.remove()
                expirations.incrementAndGet()
            }
        }
    }

    private fun isExpired(entry: Entry<V>): Boolean {
        return entry.expiresAt != Long.MAX_VALUE && entry.expiresAt <= timeSource()
    }
}
