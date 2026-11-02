package shimeserver.shimepluginlibrary.shimecommand

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 上限付きのインメモリ監査ログ。
 *
 * エントリは追加順に保持され、[maxEntries] を超えると最も古いものから削除されます。リスナーの例外は
 * 無視されるため、監査ログの記録がコマンド実行を妨げることはありません。永続化が必要な場合はリスナーを
 * 登録し、呼び出し側のストレージへ書き込んでください。
 */
class ShimeCommandAuditLog(private val maxEntries: Int = 500) {
    init {
        require(maxEntries > 0) { "Audit log size must be positive." }
    }

    private val lock = ReentrantLock()
    private val entries = ArrayDeque<ShimeCommandAuditEntry>()
    private val listeners = CopyOnWriteArrayList<(ShimeCommandAuditEntry) -> Unit>()

    /** 1 件記録する。古いエントリは上限を超えた分だけ破棄される。 */
    fun record(entry: ShimeCommandAuditEntry) {
        lock.withLock {
            entries.addLast(entry)
            while (entries.size > maxEntries) {
                entries.removeFirst()
            }
        }
        listeners.forEach { listener ->
            try {
                listener(entry)
            } catch (_: Throwable) {
                // 監査リスナーの失敗はコマンド結果へ影響させない。
            }
        }
    }

    /** 現在保持しているエントリのコピー。 */
    fun entries(): List<ShimeCommandAuditEntry> = lock.withLock { entries.toList() }

    /** 新しい順に最大 [limit] 件を返す。 */
    fun recent(limit: Int = 10): List<ShimeCommandAuditEntry> {
        require(limit >= 0) { "Limit must not be negative." }
        if (limit == 0) {
            return emptyList()
        }
        return lock.withLock {
            entries.toList().asReversed().take(limit)
        }
    }

    /** 保持しているエントリをすべて削除する。 */
    fun clear() {
        lock.withLock { entries.clear() }
    }

    /** 現在保持しているエントリ数。 */
    fun size(): Int = lock.withLock { entries.size }

    /** 記録リスナーを登録する。戻り値を閉じるか [ShimeCommandAuditSubscription.cancel] で解除する。 */
    fun addListener(listener: (ShimeCommandAuditEntry) -> Unit): ShimeCommandAuditSubscription {
        listeners += listener
        return ShimeCommandAuditSubscription { listeners.remove(listener) }
    }

    /** 登録済みのリスナーを解除する。 */
    fun removeListener(listener: (ShimeCommandAuditEntry) -> Unit): Boolean = listeners.remove(listener)
}

/** [ShimeCommandAuditLog.addListener] が返す購読ハンドル。二重に解除しても安全です。 */
class ShimeCommandAuditSubscription internal constructor(
    private val onCancel: () -> Unit
) : AutoCloseable {
    @Volatile
    private var active = true

    val isActive: Boolean
        get() = active

    fun cancel() {
        if (active) {
            active = false
            onCancel()
        }
    }

    override fun close() = cancel()
}
