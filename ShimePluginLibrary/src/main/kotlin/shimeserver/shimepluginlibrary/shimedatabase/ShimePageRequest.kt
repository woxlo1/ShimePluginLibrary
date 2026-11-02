package shimeserver.shimepluginlibrary.shimedatabase

/**
 * 0 始まりのページ番号と 1 ページあたりの件数を表す不変のリクエスト。
 *
 * 上限を設けることで、外部入力から巨大な LIMIT が渡され、サーバーのメモリを消費することを防ぎます。
 */
data class ShimePageRequest(
    val page: Int = 0,
    val size: Int = 20
) {
    init {
        require(page >= 0) { "Page index must not be negative." }
        require(size in 1..MAX_PAGE_SIZE) { "Page size must be between 1 and $MAX_PAGE_SIZE." }
    }

    /** データベースへ渡す OFFSET 値。 */
    val offset: Long
        get() = page.toLong() * size.toLong()

    companion object {
        const val MAX_PAGE_SIZE = 1_000

        /** 最初のページを要求する。 */
        @JvmStatic
        @JvmOverloads
        fun first(size: Int = 20): ShimePageRequest = ShimePageRequest(0, size)
    }
}
