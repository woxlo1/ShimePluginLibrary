package shimeserver.shimepluginlibrary.shimeevent

import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Paper のイベントリスナーをライフサイクルに紐づけて管理するサービス。
 *
 * イベントの配信自体は Paper 標準のイベントシステムに任せ、このクラスは登録したリスナーを
 * シャットダウン時に確実に解除するためだけに存在する。独自のイベントバスは持たない。
 */
class ShimeEventManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger
) : ShimeAbstractService(SERVICE_NAME) {
    private val listeners: MutableSet<Listener> = Collections.newSetFromMap(ConcurrentHashMap())

    override fun onInitialize(context: ShimeContext) {
        // リスナーは各機能が必要になった時点で登録するため、初期化時には何もしない。
    }

    override fun onShutdown() {
        listeners.forEach(HandlerList::unregisterAll)
        listeners.clear()
        logger.debug(shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory.EVENT, "登録済みの Paper リスナーを解除しました。")
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

    companion object {
        const val SERVICE_NAME = "event"
    }
}
