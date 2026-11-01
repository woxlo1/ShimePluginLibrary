package shimeserver.shimepluginlibrary.shimestorage

/**
 * 単純なキー付きドメインオブジェクト向けの永続化抽象。
 *
 * データベースを利用するストレージは、トランザクション、クエリ、ページングが本質的に異なる操作であるため
 * 別のインターフェースを使う。抽象を分けておくことで、実装の漏れを防ぐ。
 */
interface ShimeStorage<T : Any> : AutoCloseable {
    val name: String

    fun load(key: String): T?

    fun save(key: String, value: T)

    fun delete(key: String): Boolean

    fun exists(key: String): Boolean

    fun keys(): Set<String>

    override fun close()
}
