package shimeserver.shimepluginlibrary.shimedatabase

/**
 * 楽観的ロックのバージョン番号を持つエンティティ。
 *
 * リポジトリは更新時にこの値を WHERE 条件へ含め、一致した場合にのみ値を 1 増やします。別の書き込みが
 * 先にコミットしていた場合は [shimeserver.shimepluginlibrary.shimeexception.ShimeOptimisticLockException]
 * がスローされ、呼び出し側は最新の値を読み直してから再試行できます。
 */
interface ShimeVersioned {
    var version: Long
}
