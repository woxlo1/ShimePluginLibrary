package shimeserver.shimepluginlibrary.shimestorage

import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeexception.ShimeStorageException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * ファイルベースのストレージのレジストリ。
 *
 * [legacyRoot] を指定すると、各ストレージはプライマリの保存先にデータがない場合に限り、その
 * ディレクトリ配下の同名ストレージを読み取り専用で参照する。利用側プラグインごとの保存先へ
 * 移行する前にライブラリの共有フォルダへ保存されたデータを引き継ぐために使う。
 */
class ShimeStorageManager(
    private val plugin: JavaPlugin,
    private val logger: ShimeLogger,
    private val legacyRoot: Path? = null
) : ShimeAbstractService(SERVICE_NAME) {
    private val storages = ConcurrentHashMap<String, ShimeStorage<*>>()

    override fun onInitialize(context: ShimeContext) {
        Files.createDirectories(plugin.dataFolder.toPath().resolve("storage"))
    }

    override fun onShutdown() {
        storages.values.forEach { storage ->
            try {
                storage.close()
            } catch (exception: Exception) {
                logger.error(ShimeLogCategory.STORAGE, "Could not close storage '${storage.name}'.", exception)
            }
        }
        storages.clear()
    }

    fun <T : Any> createYaml(name: String, codec: ShimeStorageCodec<T>): ShimeYamlStorage<T> {
        val storage = ShimeYamlStorage(name, root(name), codec, legacyStorageRoot(name))
        register(storage)
        return storage
    }

    fun <T : Any> createJson(name: String, type: Class<T>): ShimeJsonStorage<T> {
        val storage = ShimeJsonStorage(name, root(name), type, legacyStorageRoot(name))
        register(storage)
        return storage
    }

    fun register(storage: ShimeStorage<*>, replace: Boolean = false) {
        val previous = storages.put(storage.name, storage)
        if (previous != null && !replace) {
            storages[storage.name] = previous
            throw ShimeStorageException("Storage '${storage.name}' is already registered.")
        }
    }

    fun get(name: String): ShimeStorage<*>? = storages[name]

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> require(name: String): ShimeStorage<T> {
        return (get(name) ?: throw ShimeStorageException("Storage '$name' is not registered.")) as ShimeStorage<T>
    }

    fun names(): List<String> = storages.keys.sorted()

    private fun root(name: String) = plugin.dataFolder.toPath().resolve("storage").resolve(name)

    private fun legacyStorageRoot(name: String): Path? = legacyRoot?.resolve(name)

    companion object {
        const val SERVICE_NAME = "storage"
    }
}
