package shimeserver.shimepluginlibrary.shimestorage

/** ドメインオブジェクトを、ファイルストレージが解釈できるマップとの間でシリアライズする。 */
interface ShimeStorageCodec<T : Any> {
    fun encode(value: T): Map<String, Any?>

    fun decode(data: Map<String, Any?>): T
}
