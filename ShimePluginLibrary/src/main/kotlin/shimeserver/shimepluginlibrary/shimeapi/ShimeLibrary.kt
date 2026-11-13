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
import java.util.concurrent.ConcurrentHashMap

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
 *
 * 新規コードでは [logger] や [configManager] などを直接参照するのではなく、[forPlugin] が返す
 * [ShimePluginScope] を推奨する。スコープはログ接頭辞、保存先、タスクとリスナーの所有者を
 * 利用側プラグインに揃える。既存のプロパティは 1.1.0 との互換性のために維持されている。
 */
class ShimeLibrary(private val plugin: JavaPlugin) {
    /**
     * ライブラリ本体のロガー。
     *
     * このロガーの出力は `[ShimePluginLibrary]` の接頭辞で記録される。ライブラリ自身の内部ログには
     * そのままでよいが、利用側プラグインの処理から出力する場合は [forPlugin] が返すスコープの
     * [ShimePluginScope.logger] を使い、接頭辞を利用側プラグイン名に揃えること。
     */
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
    private val scopes = ConcurrentHashMap<JavaPlugin, ShimePluginScope>()

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
            plugin.server.pluginManager.registerEvents(ShimeScopeDisableListener(this), plugin)
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
            releaseAllScopes()
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
            releaseAllScopes()
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

    /**
     * [plugin] に紐づいたスコープを返す。
     *
     * 同じプラグインに対しては常に同じインスタンスを返す。スコープは利用側プラグインの
     * `PluginDisableEvent`、または [ShimePluginScope.close] で解放される。解放後に再度呼び出すと
     * 新しいインスタンスが作られる。
     *
     * このメソッドはスレッドセーフである。複数のスレッドから同時に呼ばれても、プラグインごとに
     * 1 つのスコープだけが作られる。
     */
    fun forPlugin(plugin: JavaPlugin): ShimePluginScope {
        return scopes.computeIfAbsent(plugin) { ShimePluginScope(it, this) }
    }

    /** 現在アクティブなスコープのスナップショット。診断用。 */
    fun scopes(): List<ShimePluginScope> = scopes.values.toList()

    /** 利用側プラグインに対応するスコープを解放し、キャッシュから取り除く。 */
    internal fun releaseScope(plugin: JavaPlugin) {
        scopes.remove(plugin)?.close()
    }

    /** 解放済みのスコープをキャッシュから取り除く。[ShimePluginScope.close] から呼ばれる。 */
    internal fun forgetScope(scope: ShimePluginScope) {
        scopes.remove(scope.plugin, scope)
    }

    private fun releaseAllScopes() {
        val snapshot = scopes.values.toList()
        scopes.clear()
        snapshot.forEach { scope ->
            try {
                scope.close()
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.CORE, "Could not release the scope of '${scope.plugin.name}'.", throwable)
            }
        }
    }

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
