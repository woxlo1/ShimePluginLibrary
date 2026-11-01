package shimeserver.shimepluginlibrary.shimeapi

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimecache.ShimeCacheManager
import shimeserver.shimepluginlibrary.shimeconfig.ShimeConfigManager
import shimeserver.shimepluginlibrary.shimedatabase.ShimeDatabaseManager
import shimeserver.shimepluginlibrary.shimeeconomy.ShimeEconomyManager
import shimeserver.shimepluginlibrary.shimeentity.ShimeEntityService
import shimeserver.shimepluginlibrary.shimeevent.ShimeEventManager
import shimeserver.shimepluginlibrary.shimeexception.ShimeLifecycleException
import shimeserver.shimepluginlibrary.shimegui.ShimeGUIManager
import shimeserver.shimepluginlibrary.shimeintegration.ShimeIntegrationManager
import shimeserver.shimepluginlibrary.shimeinventory.ShimeInventoryService
import shimeserver.shimepluginlibrary.shimeitem.ShimeItemManager
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimemessage.ShimeMessageManager
import shimeserver.shimepluginlibrary.shimemodule.ShimeModuleManager
import shimeserver.shimepluginlibrary.shimeplayer.ShimePlayerManager
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimescoreboard.ShimeScoreboardManager
import shimeserver.shimepluginlibrary.shimeservice.ShimeService
import shimeserver.shimepluginlibrary.shimeservice.ShimeServiceRegistry
import shimeserver.shimepluginlibrary.shimestorage.ShimeStorageManager
import shimeserver.shimepluginlibrary.shimetask.ShimeTaskManager
import shimeserver.shimepluginlibrary.shimeworld.ShimeWorldService

/**
 * ShimePluginLibrary の中心となる bootstrap 兼公開ファサード。
 *
 * ライブラリは ShimePlugin メインクラスによって構築される。他のプラグインは [get] または [getOrNull] を通じて
 * 初期化済みのインスタンスを取得する。独自のコピーを構築してはならない。そうすると 2 つ目の
 * サービスグラフが作られ、データベースプールが重複してしまう。
 *
 * 構築時にはコアサービスが登録されるが初期化はされない。コマンドや NMS マネージャーなどの任意の
 * サービスを登録した後に [initialize] を呼び出すこと。[shutdown] はすべてのサービスを
 * 依存関係の逆順で解放し、ライブラリスケジューラー経由で作成されたタスクをキャンセルする。
 */
class ShimeLibrary(private val plugin: JavaPlugin) {
    val logger: ShimeLogger = ShimeLogger(plugin.logger)
    val scheduler: ShimeScheduler = ShimeScheduler(plugin, logger)
    val services: ShimeServiceRegistry = ShimeServiceRegistry(logger)

    val configManager = ShimeConfigManager(plugin, logger)
    val cacheManager = ShimeCacheManager()
    val storageManager = ShimeStorageManager(plugin, logger)
    val databaseManager = ShimeDatabaseManager(logger)
    val eventManager = ShimeEventManager(plugin, logger)
    val taskManager = ShimeTaskManager(scheduler)
    val messageManager = ShimeMessageManager(plugin, logger, scheduler)
    val itemManager = ShimeItemManager(plugin, logger)
    val guiManager = ShimeGUIManager(plugin, logger, scheduler)
    val playerManager = ShimePlayerManager(plugin, storageManager, scheduler, logger)
    val scoreboardManager = ShimeScoreboardManager(plugin, logger, scheduler)
    val worldService = ShimeWorldService()
    val entityService = ShimeEntityService(plugin)
    val economyManager = ShimeEconomyManager(databaseManager, logger)
    val moduleManager = ShimeModuleManager(logger)
    val integrationManager = ShimeIntegrationManager(logger)
    val inventoryService = ShimeInventoryService(scheduler)

    private val lifecycle = ShimeLifecycle()

    /** 初期化時にサービスやモジュールへ渡される不変のコンテキスト。 */
    val context: ShimeContext = ShimeContext(plugin, this, logger, scheduler, plugin.dataFolder)

    init {
        listOf(
            configManager,
            cacheManager,
            storageManager,
            databaseManager,
            eventManager,
            taskManager,
            messageManager,
            itemManager,
            guiManager,
            playerManager,
            scoreboardManager,
            worldService,
            entityService,
            economyManager,
            moduleManager,
            integrationManager,
            inventoryService
        ).forEach { services.register(it) }
    }

    val state: ShimeState
        get() = lifecycle.state

    val isReady: Boolean
        get() = lifecycle.state == ShimeState.READY

    /**
     * 初期化前に追加のサービスを登録する。
     *
     * これはライブラリに依存するためライブラリ側では構築できない、コマンドモジュールや NMS モジュールで
     * 使用される。
     */
    fun registerService(service: ShimeService, replace: Boolean = false) {
        if (lifecycle.state != ShimeState.UNINITIALIZED) {
            throw ShimeLifecycleException(
                "Services must be registered before ShimeLibrary.initialize(); current state is ${lifecycle.state}."
            )
        }
        services.register(service, replace)
    }

    /**
     * 登録済みのすべてのサービスを依存関係順に初期化する。
     *
     * 失敗した場合、ライブラリは [ShimeState.FAILED] に遷移し、呼び出し元はすでに初期化された
     * サービスを解放するために [shutdown] を呼び出すべきである。
     */
    fun initialize() {
        if (lifecycle.state == ShimeState.READY) {
            return
        }
        lifecycle.transition(
            "ShimeLibrary",
            "initialize",
            setOf(ShimeState.UNINITIALIZED, ShimeState.FAILED),
            ShimeState.INITIALIZING
        )
        try {
            logger.info(ShimeLogCategory.CORE, "Initializing ShimePluginLibrary ${plugin.pluginMeta.version}.")
            services.initializeAll(context)
            lifecycle.force(ShimeState.READY)
            instance = this
            logger.info(ShimeLogCategory.CORE, "ShimePluginLibrary is ready.")
        } catch (throwable: Throwable) {
            lifecycle.force(ShimeState.FAILED)
            logger.error(ShimeLogCategory.CORE, "ShimePluginLibrary initialization failed.", throwable)
            throw throwable
        }
    }

    /**
     * すべてのサービスを初期化の逆順で停止する。
     *
     * このメソッドは冪等であり、初期化に失敗した後でも安全に呼び出せる。個々のサービスの失敗は
     * レジストリによってログに記録され、残りのサービスの解放を妨げない。
     */
    fun shutdown() {
        val current = lifecycle.state
        if (current == ShimeState.STOPPED) {
            return
        }
        if (current == ShimeState.UNINITIALIZED) {
            scheduler.cancelAll()
            lifecycle.force(ShimeState.STOPPED)
            return
        }
        lifecycle.transition(
            "ShimeLibrary",
            "shutdown",
            setOf(ShimeState.READY, ShimeState.FAILED, ShimeState.INITIALIZING),
            ShimeState.STOPPING
        )
        try {
            services.shutdownAll()
        } finally {
            scheduler.cancelAll()
            lifecycle.force(ShimeState.STOPPED)
            if (instance === this) {
                instance = null
            }
            logger.info(ShimeLogCategory.CORE, "ShimePluginLibrary has been shut down.")
        }
    }

    /** ライブラリが準備完了でない場合にスローする。 */
    fun requireReady(operation: String) {
        lifecycle.requireReady("ShimeLibrary", operation)
    }

    fun <T : ShimeService> service(type: Class<T>): T = services.require(type)

    fun serviceByName(name: String): ShimeService = services.requireByName(name)

    fun serviceStates(): Map<String, ShimeState> = services.snapshot()

    companion object {
        @Volatile
        private var instance: ShimeLibrary? = null

        /**
         * 初期化済みのライブラリインスタンスを返す。
         *
         * @throws ShimeLifecycleException ライブラリがまだ初期化されていないか、停止済みの場合。
         */
        @JvmStatic
        fun get(): ShimeLibrary {
            val current = instance
                ?: throw ShimeLifecycleException(
                    "ShimePluginLibrary is not initialized. Declare a dependency on ShimePluginLibrary in plugin.yml " +
                        "and access the API after the server has enabled it."
                )
            current.requireReady("provide the public API")
            return current
        }

        /** ライブラリが現在準備完了である場合にのみライブラリを返す。 */
        @JvmStatic
        fun getOrNull(): ShimeLibrary? {
            val current = instance ?: return null
            return current.takeIf { it.isReady }
        }
    }
}
