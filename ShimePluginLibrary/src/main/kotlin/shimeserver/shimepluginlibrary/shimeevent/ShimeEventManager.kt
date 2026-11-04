package shimeserver.shimepluginlibrary.shimeevent

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Paper のイベントリスナーをライフサイクルに紐づけて管理するサービス。
 *
 * イベントの配信自体は Paper 標準のイベントシステムに任せ、このクラスは登録したリスナーを
 * シャットダウン時に確実に解除するためだけに存在する。独自のイベントバスは持たない。
 *
 * [subscribe] はフィルター、実行時間計測、例外保護、デバッグログを備えた購読を作成する。
 * 非同期イベントと同期イベントは Paper の [Event.isAsynchronous] で区別でき、
 * [subscribe] の `requirePrimaryThread` を true にするとプライマリスレッド以外では処理しない。
 */
class ShimeEventManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger
) : ShimeAbstractService(SERVICE_NAME) {
    private val listeners: MutableSet<Listener> = Collections.newSetFromMap(ConcurrentHashMap())
    private val subscriptions = ConcurrentHashMap<String, ShimeEventSubscription>()
    private val sequence = AtomicLong()

    override fun onInitialize(context: ShimeContext) {
        // リスナーは各機能が必要になった時点で登録するため、初期化時には何もしない。
    }

    override fun onShutdown() {
        subscriptions.values.toList().forEach { it.unregister() }
        subscriptions.clear()
        listeners.forEach(HandlerList::unregisterAll)
        listeners.clear()
        logger.debug(ShimeLogCategory.EVENT, "登録済みの Paper リスナーを解除しました。")
    }

    /**
     * Paper のイベントリスナーを登録し、このサービスで管理する。
     *
     * 同じリスナーを複数回登録した場合も Paper 側の重複登録として扱われるため、呼び出し側は
     * 登録済みかどうかを [isRegistered] で確認できる。
     */
    fun registerListener(listener: Listener) {
        plugin.server.pluginManager.registerEvents(listener, plugin)
        listeners.add(listener)
    }

    /** 管理対象のリスナーを解除する。未登録の場合は `false` を返す。 */
    fun unregisterListener(listener: Listener): Boolean {
        if (!listeners.remove(listener)) {
            return false
        }
        HandlerList.unregisterAll(listener)
        return true
    }

    /** このサービスがリスナーを管理しているかを返す。 */
    fun isRegistered(listener: Listener): Boolean = listeners.contains(listener)

    /** 管理中のリスナー数を返す。 */
    fun listenerCount(): Int = listeners.size

    /**
     * 条件付きのイベント購読を登録する。
     *
     * @param eventType 購読するイベント型。
     * @param priority Paper の実行優先度。購読間の順序制御に使う。
     * @param ignoreCancelled キャンセル済みイベントを無視するか。
     * @param requirePrimaryThread true の場合、プライマリスレッド以外で発火されたイベントを無視する。
     * @param filter ハンドラー実行前の条件。false を返すとハンドラーは呼ばれない。
     * @param handler イベント本体。例外はログに記録され、他のリスナーへは伝播しない。
     */
    @JvmOverloads
    fun <T : Event> subscribe(
        eventType: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        requirePrimaryThread: Boolean = false,
        filter: (T) -> Boolean = { true },
        handler: (T) -> Unit
    ): ShimeEventSubscription {
        val id = "subscription-" + sequence.incrementAndGet()
        val listener = object : Listener {}
        val subscription = ShimeEventSubscription(
            id = id,
            eventType = eventType,
            priority = priority,
            ignoreCancelled = ignoreCancelled,
            listener = listener,
            onUnregister = { removed ->
                subscriptions.remove(removed.id)
                listeners.remove(removed.listener)
                HandlerList.unregisterAll(removed.listener)
            }
        )
        @Suppress("UNCHECKED_CAST")
        plugin.server.pluginManager.registerEvent(
            eventType as Class<out Event>,
            listener,
            priority,
            EventExecutor { _, event ->
                if (!eventType.isInstance(event)) {
                    return@EventExecutor
                }
                if (requirePrimaryThread && !Bukkit.isPrimaryThread()) {
                    subscription.recordFiltered()
                    return@EventExecutor
                }
                val typed = event as T
                val started = System.nanoTime()
                var failed = false
                try {
                    if (!filter(typed)) {
                        subscription.recordFiltered()
                        return@EventExecutor
                    }
                    handler(typed)
                } catch (throwable: Throwable) {
                    failed = true
                    logger.error(
                        ShimeLogCategory.EVENT,
                        "Event subscription '$id' failed for ${eventType.simpleName}.",
                        throwable
                    )
                } finally {
                    subscription.recordHandled(System.nanoTime() - started, failed)
                }
                if (logger.isDebugEnabled) {
                    logger.debug(
                        ShimeLogCategory.EVENT,
                        "Subscription '$id' handled ${eventType.simpleName} (async=${event.isAsynchronous})."
                    )
                }
            },
            plugin,
            ignoreCancelled
        )
        listeners.add(listener)
        subscriptions[id] = subscription
        return subscription
    }

    /** [id] の購読を解除する。 */
    fun unsubscribe(id: String): Boolean = subscriptions[id]?.unregister() == true

    /** すべての購読を解除する。通常のリスナー登録は維持される。 */
    fun unsubscribeAll() {
        subscriptions.values.toList().forEach { it.unregister() }
        subscriptions.clear()
    }

    /** 登録済みの購読を返す。 */
    fun subscriptions(): List<ShimeEventSubscription> = subscriptions.values.sortedBy { it.id }

    /** 登録済み購読の統計を返す。 */
    fun statistics(): List<ShimeEventSubscriptionStats> {
        return subscriptions.values.map { it.snapshot() }.sortedBy { it.id }
    }

    /** [event] が非同期イベントかどうか。 */
    fun isAsync(event: Event): Boolean = event.isAsynchronous

    companion object {
        const val SERVICE_NAME = "event"
    }
}
