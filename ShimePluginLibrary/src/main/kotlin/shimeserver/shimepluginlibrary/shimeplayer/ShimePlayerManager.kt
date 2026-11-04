package shimeserver.shimepluginlibrary.shimeplayer

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeexception.ShimeStorageException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimescheduler.ShimeScheduler
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import shimeserver.shimepluginlibrary.shimestorage.ShimeStorage
import shimeserver.shimepluginlibrary.shimestorage.ShimeStorageManager
import shimeserver.shimepluginlibrary.shimeutil.ShimeFiles
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * プレイヤープロフィールの読み込み、キャッシュ、永続化を行います。
 *
 * プロフィールの I/O は非同期で実行されます。プロフィールは読み込みが完了してからキャッシュに公開されるため、
 * 利用側が読み込み途中のレコードを目にすることはありません。退出時にはプロフィールを退避する前に保存します。
 */
class ShimePlayerManager(
    private val plugin: JavaPlugin,
    private val storageManager: ShimeStorageManager,
    private val scheduler: ShimeScheduler,
    private val logger: ShimeLogger
) : ShimeAbstractService(SERVICE_NAME, 0, setOf(ShimeStorageManager.SERVICE_NAME)), Listener {
    private val profiles = ConcurrentHashMap<UUID, ShimePlayerProfile>()
    private val loading = ConcurrentHashMap<UUID, CompletableFuture<ShimePlayerProfile>>()
    private val sessionStarts = ConcurrentHashMap<UUID, Long>()
    private val sessions = ConcurrentHashMap<UUID, ShimePlayerSession>()
    private val dirty = ConcurrentHashMap.newKeySet<UUID>()
    private val pendingQuits = ConcurrentHashMap.newKeySet<UUID>()
    private lateinit var storage: ShimeStorage<ShimePlayerProfile>

    override fun onInitialize(context: ShimeContext) {
        storage = storageManager.createYaml("players", ShimePlayerProfileCodec)
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onShutdown() {
        HandlerList.unregisterAll(this)
        sessions.values.forEach { session ->
            try {
                session.close()
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.PLAYER, "Could not close session resources for '${session.uuid}'.", throwable)
            }
        }
        sessions.clear()
        saveAll()
        sessionStarts.clear()
        profiles.clear()
        loading.clear()
        pendingQuits.clear()
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val now = System.currentTimeMillis()
        sessionStarts[player.uniqueId] = now
        sessions.remove(player.uniqueId)?.let { previous ->
            try {
                previous.close()
            } catch (throwable: Throwable) {
                logger.warn(ShimeLogCategory.PLAYER, "Could not close the previous session of ${player.name}.", throwable)
            }
        }
        sessions[player.uniqueId] = ShimePlayerSession(player.uniqueId, now)
        loadProfileAsync(player.uniqueId, player.name, publish = false).whenComplete { profile, throwable ->
            if (throwable != null) {
                logger.error(
                    ShimeLogCategory.PLAYER,
                    "Could not load profile for ${player.name}.",
                    throwable
                )
                return@whenComplete
            }
            if (profile == null) {
                return@whenComplete
            }
            // プロフィールの読み込み中にプレイヤーが切断している可能性があります。その場合の永続化は
            // 退出ハンドラーが担当し、ここでプロフィールを公開すると古いセッションが復活してしまいます。
            if (sessionStarts[player.uniqueId] != now ||
                player.uniqueId in pendingQuits ||
                plugin.server.getPlayer(player.uniqueId)?.isOnline != true
            ) {
                return@whenComplete
            }
            profile.name = player.name
            profile.lastJoinMillis = now
            profile.locale = player.locale().toString()
            profile.sessionCount += 1
            profile.addLogin(ShimeLoginRecord(now))
            profiles[player.uniqueId] = profile
            dirty.add(player.uniqueId)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        val started = sessionStarts.remove(uuid) ?: now
        sessions.remove(uuid)?.let { session ->
            try {
                session.close()
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.PLAYER, "Could not close session resources for ${player.name}.", throwable)
            }
        }
        val profile = profiles[uuid]
        if (profile == null) {
            // 参加時の読み込みがまだ実行中の可能性があります。参加コールバックがプロフィールを公開しないよう
            // 先に退出を記録し、共有の読み込みが完了したら永続化します。
            pendingQuits += uuid
            loadProfileAsync(uuid, player.name, publish = false).whenComplete { loaded, throwable ->
                pendingQuits.remove(uuid)
                if (throwable != null) {
                    logger.error(
                        ShimeLogCategory.PLAYER,
                        "Could not load the profile of ${player.name} while handling a quit.",
                        throwable
                    )
                    return@whenComplete
                }
                if (loaded == null) {
                    return@whenComplete
                }
                loaded.name = player.name
                loaded.lastQuitMillis = now
                loaded.playTimeMillis += (now - started).coerceAtLeast(0)
                loaded.closeActiveLogin(now)
                dirty.add(uuid)
                scheduler.runAsync {
                    try {
                        saveProfile(loaded)
                        dirty.remove(uuid)
                    } catch (saveFailure: Throwable) {
                        logger.error(
                            ShimeLogCategory.PLAYER,
                            "Could not save the profile of ${player.name} after a quit during loading.",
                            saveFailure
                        )
                    } finally {
                        profiles.remove(uuid, loaded)
                    }
                }
            }
            return
        }
        profile.lastQuitMillis = now
        profile.playTimeMillis += (now - started).coerceAtLeast(0)
        profile.closeActiveLogin(now)
        dirty.add(uuid)
        scheduler.runAsync {
            try {
                saveProfile(profile)
                dirty.remove(uuid)
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.PLAYER, "Could not save profile for ${player.name}.", throwable)
            } finally {
                profiles.remove(uuid, profile)
            }
        }
    }

    fun profile(uuid: UUID): ShimePlayerProfile? = profiles[uuid]

    fun profile(name: String): ShimePlayerProfile? {
        val player = Bukkit.getPlayerExact(name)
        if (player != null) {
            return profiles[player.uniqueId]
        }
        return profiles.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun requireProfile(uuid: UUID): ShimePlayerProfile {
        return profile(uuid) ?: throw IllegalStateException(
            "Profile for '$uuid' is not loaded. Use loadProfileAsync() before accessing offline data."
        )
    }

    fun onlineProfiles(): List<ShimePlayerProfile> = profiles.values.toList()

    /**
     * ストレージからプロフィールを読み込み、存在しない場合は新規に作成します。
     * 同じ UUID に対する同時呼び出しは 1 つの読み込み処理を共有します。
     */
    @JvmOverloads
    fun loadProfileAsync(
        uuid: UUID,
        name: String? = null,
        publish: Boolean = true
    ): CompletableFuture<ShimePlayerProfile> {
        profiles[uuid]?.let { return CompletableFuture.completedFuture(it) }
        val existing = loading[uuid]
        if (existing != null) {
            return existing
        }
        val future = CompletableFuture<ShimePlayerProfile>()
        loading[uuid] = future
        scheduler.runAsync {
            try {
                val stored = storage.load(uuid.toString())
                val profile = stored ?: ShimePlayerProfile(
                    uuid = uuid,
                    name = name ?: uuid.toString()
                )
                if (name != null) {
                    profile.name = name
                }
                if (publish) {
                    profiles[uuid] = profile
                }
                future.complete(profile)
            } catch (throwable: Throwable) {
                future.completeExceptionally(throwable)
            } finally {
                loading.remove(uuid, future)
            }
        }
        return future
    }

    fun saveProfile(profile: ShimePlayerProfile) {
        try {
            storage.save(profile.uuid.toString(), profile)
        } catch (exception: ShimeStorageException) {
            throw exception
        }
    }

    fun saveAsync(uuid: UUID): CompletableFuture<Boolean> {
        val profile = profiles[uuid]
        if (profile == null) {
            return CompletableFuture.completedFuture(false)
        }
        return CompletableFuture.supplyAsync {
            saveProfile(profile)
            dirty.remove(uuid)
            true
        }
    }

    fun saveAll() {
        val snapshot = profiles.values.toList()
        snapshot.forEach { profile ->
            try {
                saveProfile(profile)
                dirty.remove(profile.uuid)
            } catch (exception: Exception) {
                logger.error(ShimeLogCategory.PLAYER, "Could not save profile for '${profile.name}'.", exception)
            }
        }
    }

    fun isDirty(uuid: UUID): Boolean = uuid in dirty

    fun dirtyProfiles(): Set<UUID> = dirty.toSet()

    fun setLocale(uuid: UUID, locale: String) {
        profiles[uuid]?.let {
            it.locale = locale
            dirty.add(uuid)
        }
    }

    fun playTimeMillis(uuid: UUID): Long {
        val profile = profiles[uuid]
        val stored = profile?.playTimeMillis ?: 0L
        val started = sessionStarts[uuid] ?: return stored
        return stored + (System.currentTimeMillis() - started).coerceAtLeast(0)
    }

    /** オンライン中の [uuid] のセッション。オフラインの場合は null。 */
    fun session(uuid: UUID): ShimePlayerSession? = sessions[uuid]

    /**
     * セッション終了時に自動で閉じるリソースを登録する。
     *
     * @return セッションが存在しない場合は false。
     */
    fun registerSessionResource(uuid: UUID, resource: AutoCloseable): Boolean {
        val session = sessions[uuid] ?: return false
        session.register(resource)
        return true
    }

    /** [uuid] のログイン履歴を新しい順に返す。 */
    fun loginHistory(uuid: UUID, limit: Int = ShimePlayerProfile.MAX_LOGIN_HISTORY): List<ShimeLoginRecord> {
        require(limit >= 0) { "History limit must not be negative." }
        return profiles[uuid]?.recentLogins(limit) ?: emptyList()
    }

    /** [uuid] の集計値を返す。プロフィールが未読み込みの場合は null。 */
    fun statistics(uuid: UUID): ShimePlayerStatistics? {
        val profile = profiles[uuid] ?: return null
        return ShimePlayerStatistics(
            uuid = profile.uuid,
            name = profile.name,
            sessionCount = profile.sessionCount,
            totalPlayTimeMillis = playTimeMillis(uuid),
            lastJoinMillis = profile.lastJoinMillis,
            lastQuitMillis = profile.lastQuitMillis
        )
    }

    /**
     * [uuid] のプロフィールをマップへエクスポートする。
     *
     * 読み込みは非同期で行われます。返されたマップは [importProfile] にそのまま渡せます。
     */
    fun exportProfile(uuid: UUID): CompletableFuture<Map<String, Any?>> {
        return loadProfileAsync(uuid, publish = false).thenApply { profile ->
            ShimePlayerProfileCodec.encode(profile)
        }
    }

    /** [uuid] のプロフィールを YAML ファイルへエクスポートする。 */
    fun exportProfileToFile(uuid: UUID, file: File): CompletableFuture<Boolean> {
        return exportProfile(uuid).thenApply { data ->
            val yaml = YamlConfiguration()
            data.forEach { (key, value) -> yaml.set(key, value) }
            ShimeFiles.atomicWrite(file.toPath(), yaml.saveToString())
            true
        }
    }

    /** マップからプロフィールをインポートし、保存と公開を行う。 */
    fun importProfile(data: Map<String, Any?>): CompletableFuture<ShimePlayerProfile> {
        val future = CompletableFuture<ShimePlayerProfile>()
        scheduler.runAsync {
            try {
                val profile = ShimePlayerProfileCodec.decode(data)
                saveProfile(profile)
                profiles[profile.uuid] = profile
                dirty.remove(profile.uuid)
                future.complete(profile)
            } catch (throwable: Throwable) {
                future.completeExceptionally(throwable)
            }
        }
        return future
    }

    /** YAML ファイルからプロフィールをインポートする。 */
    fun importProfileFromFile(file: File): CompletableFuture<ShimePlayerProfile> {
        val future = CompletableFuture<ShimePlayerProfile>()
        scheduler.runAsync {
            try {
                val yaml = YamlConfiguration.loadConfiguration(file)
                @Suppress("UNCHECKED_CAST")
                val data = yaml.getValues(false) as Map<String, Any?>
                val profile = ShimePlayerProfileCodec.decode(data)
                saveProfile(profile)
                profiles[profile.uuid] = profile
                dirty.remove(profile.uuid)
                future.complete(profile)
            } catch (throwable: Throwable) {
                future.completeExceptionally(throwable)
            }
        }
        return future
    }

    companion object {
        const val SERVICE_NAME = "player"
    }
}
