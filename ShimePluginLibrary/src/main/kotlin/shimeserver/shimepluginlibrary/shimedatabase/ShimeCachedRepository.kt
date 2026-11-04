package shimeserver.shimepluginlibrary.shimedatabase

import shimeserver.shimepluginlibrary.shimecache.ShimeCache

/**
 * [ShimeRepository] を [ShimeCache] で包む読み取りキャッシュ。
 *
 * 読み取りは主キー単位でキャッシュされ、[save] と [deleteById] はキャッシュを同期して更新します。
 * キャッシュ値は保存済みエンティティそのものなので、呼び出し側が取得後に変更した場合は
 * [invalidate] を呼ぶか、変更を保存してキャッシュを更新してください。
 *
 * [findAll] と [findPage] は常に委譲先へ問い合わせます。大量データを一括でキャッシュすると
 * メモリを圧迫するためです。
 *
 * @param ttlMillis キャッシュの有効期限。null の場合はキャッシュ既定値を使います。
 * @param keyExtractor 保存時にキャッシュキーを取り出す関数。主キーの取得に使用します。
 */
class ShimeCachedRepository<T : Any, ID : Any>(
    private val delegate: ShimeRepository<T, ID>,
    private val cache: ShimeCache<ID, T>,
    private val keyExtractor: (T) -> ID,
    private val ttlMillis: Long? = null
) : ShimeRepository<T, ID> {
    init {
        require(ttlMillis == null || ttlMillis >= 0L) { "Cache TTL must not be negative." }
    }

    override val name: String
        get() = delegate.name

    override fun findById(id: ID): T? {
        cache.get(id)?.let { return it }
        val loaded = delegate.findById(id) ?: return null
        cache.put(id, loaded, ttlMillis)
        return loaded
    }

    override fun findAll(): List<T> = delegate.findAll()

    override fun findPage(request: ShimePageRequest): ShimePage<T> = delegate.findPage(request)

    override fun save(entity: T): T {
        val saved = delegate.save(entity)
        cache.put(keyExtractor(saved), saved, ttlMillis)
        return saved
    }

    override fun saveAll(entities: List<T>): Int {
        val affected = delegate.saveAll(entities)
        if (affected > 0) {
            entities.forEach { entity -> cache.put(keyExtractor(entity), entity, ttlMillis) }
        }
        return affected
    }

    override fun deleteById(id: ID): Boolean {
        val deleted = delegate.deleteById(id)
        if (deleted) {
            cache.remove(id)
        }
        return deleted
    }

    override fun count(): Long = delegate.count()

    override fun existsById(id: ID): Boolean {
        return cache.contains(id) || delegate.existsById(id)
    }

    /**
     * 主キー [id] のキャッシュだけを破棄する。
     *
     * @return 破棄した値があった場合に true。
     */
    fun invalidate(id: ID): Boolean = cache.remove(id) != null

    /** キャッシュ全体を破棄する。委譲先のデータには影響しません。 */
    fun invalidateAll() {
        cache.clear()
    }

    /** 委譲先のリポジトリ。 */
    fun delegate(): ShimeRepository<T, ID> = delegate
}
