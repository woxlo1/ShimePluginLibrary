package shimeserver.shimepluginlibrary.shimecache

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * ユーザーが提供するローダーを使って、存在しない値を読み込むキャッシュ。
 *
 * 同じキーに対する同時リクエストは 1 回の読み込み処理を共有する。これにより、遅いデータベースクエリが
 * 待機中のプレイヤーごとに実行される事態（キャッシュスタンピード）を防ぐ。
 */
class ShimeLoadingCache<K : Any, V : Any>(
    private val delegate: ShimeCache<K, V>,
    private val loader: (K) -> V?
) : ShimeCache<K, V> {
    private val inFlight = ConcurrentHashMap<K, CompletableFuture<V?>>()
    private val loads = AtomicLong()
    private val loadFailures = AtomicLong()

    fun getOrLoad(key: K): V? {
        delegate.get(key)?.let { return it }
        val created = CompletableFuture<V?>()
        val existing = inFlight.putIfAbsent(key, created)
        if (existing != null) {
            return existing.join()
        }
        return try {
            val loaded = loader(key)
            loads.incrementAndGet()
            if (loaded != null) {
                delegate.put(key, loaded)
            }
            created.complete(loaded)
            loaded
        } catch (throwable: Throwable) {
            loadFailures.incrementAndGet()
            created.completeExceptionally(throwable)
            throw throwable
        } finally {
            inFlight.remove(key, created)
        }
    }

    fun getOrLoadAsync(key: K, executor: Executor): CompletableFuture<V?> {
        delegate.get(key)?.let { return CompletableFuture.completedFuture(it) }
        val created = CompletableFuture<V?>()
        val existing = inFlight.putIfAbsent(key, created)
        if (existing != null) {
            return existing
        }
        executor.execute {
            try {
                val loaded = loader(key)
                loads.incrementAndGet()
                if (loaded != null) {
                    delegate.put(key, loaded)
                }
                created.complete(loaded)
            } catch (throwable: Throwable) {
                loadFailures.incrementAndGet()
                created.completeExceptionally(throwable)
            } finally {
                inFlight.remove(key, created)
            }
        }
        return created
    }

    fun loadCount(): Long = loads.get()

    fun loadFailureCount(): Long = loadFailures.get()

    override fun get(key: K): V? = delegate.get(key)

    override fun put(key: K, value: V, ttlMillis: Long?): V? = delegate.put(key, value, ttlMillis)

    override fun remove(key: K): V? = delegate.remove(key)

    override fun contains(key: K): Boolean = delegate.contains(key)

    override fun clear() {
        delegate.clear()
        inFlight.clear()
    }

    override fun size(): Int = delegate.size()

    override fun keys(): Set<K> = delegate.keys()

    override fun stats(): ShimeCacheStats = delegate.stats()
}
