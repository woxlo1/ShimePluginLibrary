package shimeserver.shimepluginlibrary.shimedatabase

/**
 * ページングされたクエリ結果。
 *
 * [totalElements] は件数クエリの結果であり、[content] のサイズとは独立しています。ページ範囲外を
 * 要求した場合でも、[content] は空になるだけで例外にはなりません。
 */
data class ShimePage<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long
) {
    init {
        require(page >= 0) { "Page index must not be negative." }
        require(size > 0) { "Page size must be greater than zero." }
        require(totalElements >= 0) { "Total element count must not be negative." }
    }

    /** 総ページ数。要素が 1 件もない場合は 0 を返します。 */
    val totalPages: Int
        get() = if (totalElements == 0L) 0 else ((totalElements + size - 1) / size).toInt()

    val hasNext: Boolean
        get() = page + 1 < totalPages

    val hasPrevious: Boolean
        get() = page > 0

    val isEmpty: Boolean
        get() = content.isEmpty()

    fun <R> map(transform: (T) -> R): ShimePage<R> {
        return ShimePage(content.map(transform), page, size, totalElements)
    }

    companion object {
        /** 空のページを作成する。 */
        @JvmStatic
        fun <T> empty(page: ShimePageRequest): ShimePage<T> {
            return ShimePage(emptyList(), page.page, page.size, 0L)
        }
    }
}
