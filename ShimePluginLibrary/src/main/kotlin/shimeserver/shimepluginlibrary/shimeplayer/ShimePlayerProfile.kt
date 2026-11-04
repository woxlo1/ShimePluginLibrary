package shimeserver.shimepluginlibrary.shimeplayer

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * プレイヤーマネージャーがキャッシュする可変のプレイヤープロフィール。
 *
 * 永続化のためのアクセスはマネージャーによって同期されます。各フィールドは意図的に単純にしてあり、
 * 機能プラグインが別のプロフィールシステムを作らずに小さなメタデータ値を付けられるようになっています。
 * ログイン履歴は上限付きで保持され、古いものから破棄されます。
 */
class ShimePlayerProfile(
    val uuid: UUID,
    var name: String,
    var firstJoinMillis: Long = System.currentTimeMillis(),
    var lastJoinMillis: Long = System.currentTimeMillis(),
    var lastQuitMillis: Long = 0L,
    var playTimeMillis: Long = 0L,
    var locale: String = "en_US",
    val metadata: MutableMap<String, String> = ConcurrentHashMap(),
    var sessionCount: Int = 0,
    val loginHistory: MutableList<ShimeLoginRecord> = CopyOnWriteArrayList()
) {
    fun metadata(key: String, defaultValue: String? = null): String? = metadata[key] ?: defaultValue

    fun setMetadata(key: String, value: String?) {
        if (value == null) {
            metadata.remove(key)
        } else {
            metadata[key] = value
        }
    }

    /** ログイン履歴を追加し、上限を超えた古い履歴を削除する。 */
    fun addLogin(record: ShimeLoginRecord) {
        loginHistory += record
        while (loginHistory.size > MAX_LOGIN_HISTORY) {
            loginHistory.removeAt(0)
        }
    }

    /** 継続中の最新セッションを閉じる。 */
    fun closeActiveLogin(quitMillis: Long = System.currentTimeMillis()): ShimeLoginRecord? {
        for (index in loginHistory.indices.reversed()) {
            val record = loginHistory[index]
            if (record.isActive) {
                val closed = record.copy(quitMillis = quitMillis.coerceAtLeast(record.joinMillis))
                loginHistory[index] = closed
                return closed
            }
        }
        return null
    }

    /** 新しい順のログイン履歴。 */
    fun recentLogins(limit: Int = MAX_LOGIN_HISTORY): List<ShimeLoginRecord> {
        require(limit >= 0) { "History limit must not be negative." }
        return loginHistory.asReversed().take(limit)
    }

    fun copy(): ShimePlayerProfile {
        return ShimePlayerProfile(
            uuid = uuid,
            name = name,
            firstJoinMillis = firstJoinMillis,
            lastJoinMillis = lastJoinMillis,
            lastQuitMillis = lastQuitMillis,
            playTimeMillis = playTimeMillis,
            locale = locale,
            metadata = ConcurrentHashMap(metadata),
            sessionCount = sessionCount,
            loginHistory = CopyOnWriteArrayList(loginHistory)
        )
    }

    companion object {
        /** 保持するログイン履歴の最大件数。 */
        const val MAX_LOGIN_HISTORY = 50
    }
}
