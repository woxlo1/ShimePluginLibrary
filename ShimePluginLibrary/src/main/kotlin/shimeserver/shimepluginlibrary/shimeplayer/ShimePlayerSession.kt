package shimeserver.shimepluginlibrary.shimeplayer

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 1 回のログインセッションを表す履歴レコード。
 *
 * [quitMillis] が 0 の場合はまだ継続中のセッションとして扱われます。
 */
data class ShimeLoginRecord(
    val joinMillis: Long,
    val quitMillis: Long = 0L
) {
    init {
        require(joinMillis > 0L) { "Login time must be positive." }
        require(quitMillis == 0L || quitMillis >= joinMillis) {
            "Logout time must not precede the login time."
        }
    }

    val isActive: Boolean
        get() = quitMillis == 0L

    /** セッションの継続時間。継続中の場合は [nowMillis] までを返す。 */
    fun durationMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val end = if (quitMillis > 0L) quitMillis else nowMillis
        return (end - joinMillis).coerceAtLeast(0L)
    }
}

/**
 * プレイヤーのログイン中セッションに紐づくリソース管理。
 *
 * プレイヤー退出時に登録したリソースを逆順で閉じます。リソースのクローズで例外が発生しても
 * 残りのリソースは閉じられ、例外は集約されます。
 */
class ShimePlayerSession internal constructor(
    val uuid: UUID,
    val startedAtMillis: Long = System.currentTimeMillis()
) {
    private val resources = CopyOnWriteArrayList<AutoCloseable>()
    private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()

    @Volatile
    private var closed: Boolean = false

    val isClosed: Boolean
        get() = closed

    /**
     * セッション終了時に閉じるリソースを登録する。
     *
     * @throws IllegalStateException セッションがすでに閉じている場合。
     */
    fun register(resource: AutoCloseable) {
        check(!closed) { "Session for '$uuid' is already closed." }
        resources += resource
    }

    /** セッションに任意の値を紐づける。 */
    fun put(key: String, value: Any) {
        require(key.isNotBlank()) { "Session value key must not be blank." }
        check(!closed) { "Session for '$uuid' is already closed." }
        values[key] = value
    }

    /** セッションに紐づけた値を取得する。 */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = values[key] as? T

    /** セッションに紐づけた値を削除する。 */
    fun remove(key: String): Any? = values.remove(key)

    /** セッションの経過時間。 */
    fun elapsedMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return (nowMillis - startedAtMillis).coerceAtLeast(0L)
    }

    /** 登録済みリソースを逆順で閉じ、セッションを終了済みにする。 */
    fun close() {
        if (closed) {
            return
        }
        closed = true
        var failure: Throwable? = null
        resources.reversed().forEach { resource ->
            try {
                resource.close()
            } catch (throwable: Throwable) {
                if (failure == null) {
                    failure = throwable
                } else {
                    failure!!.addSuppressed(throwable)
                }
            }
        }
        resources.clear()
        values.clear()
        failure?.let { throw java.lang.IllegalStateException("One or more session resources failed to close.", it) }
    }
}

/** プレイヤー 1 人分の集計値。 */
data class ShimePlayerStatistics(
    val uuid: UUID,
    val name: String,
    val sessionCount: Int,
    val totalPlayTimeMillis: Long,
    val lastJoinMillis: Long,
    val lastQuitMillis: Long
) {
    init {
        require(sessionCount >= 0) { "Session count must not be negative." }
        require(totalPlayTimeMillis >= 0L) { "Total play time must not be negative." }
    }

    /** 平均セッション時間。 */
    val averageSessionMillis: Long
        get() = if (sessionCount == 0) 0L else totalPlayTimeMillis / sessionCount
}
