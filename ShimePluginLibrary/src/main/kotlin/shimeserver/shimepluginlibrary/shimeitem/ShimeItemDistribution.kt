package shimeserver.shimepluginlibrary.shimeitem

/**
 * アイテム配布の結果。
 *
 * [requested] は要求した個数、[inserted] はインベントリへ格納できた個数、[dropped] はプレイヤーの位置に
 * ドロップした個数です。常に `inserted + dropped == requested` が成り立ちます。
 */
data class ShimeItemDistribution(
    val requested: Int,
    val inserted: Int,
    val dropped: Int
) {
    init {
        require(requested >= 0) { "Requested amount must not be negative." }
        require(inserted >= 0) { "Inserted amount must not be negative." }
        require(dropped >= 0) { "Dropped amount must not be negative." }
        require(inserted + dropped == requested) { "Inserted and dropped amounts must add up to the requested amount." }
    }

    /** すべてインベントリへ格納できた場合に true。 */
    val fullyInserted: Boolean
        get() = dropped == 0
}
