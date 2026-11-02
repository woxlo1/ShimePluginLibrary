package shimeserver.shimepluginlibrary.shimedatabase

/**
 * 単一テーブルに対する型安全な永続化契約。
 *
 * 実装は SQL を外部入力から組み立ててはならず、値は必ずパラメーターとしてバインドします。
 * 読み取りは [ShimeDatabase] のクエリ API を通じて行い、書き込みは実装側のトランザクション方針に従います。
 */
interface ShimeRepository<T : Any, ID : Any> {
    /** 診断ログに表示するリポジトリ名。 */
    val name: String

    /** 主キーで 1 件取得する。存在しない場合は null。 */
    fun findById(id: ID): T?

    /** 並び順に従って全件取得する。大量データでは [findPage] を使用すること。 */
    fun findAll(): List<T>

    /** 件数とページ範囲を指定して取得する。 */
    fun findPage(request: ShimePageRequest): ShimePage<T>

    /** 1 件を挿入または更新する。 */
    fun save(entity: T): T

    /** 複数件をまとめて挿入または更新し、処理した件数を返す。 */
    fun saveAll(entities: List<T>): Int

    /** 主キーで削除し、削除できた場合に true を返す。 */
    fun deleteById(id: ID): Boolean

    /** 総件数を返す。 */
    fun count(): Long

    /** 主キーが存在する場合に true を返す。 */
    fun existsById(id: ID): Boolean = findById(id) != null
}
