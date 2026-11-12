package shimeserver.shimepluginlibrary.shimestorage

import com.google.gson.GsonBuilder
import shimeserver.shimepluginlibrary.shimeexception.ShimeStorageException
import shimeserver.shimepluginlibrary.shimeutil.ShimeFiles
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation
import java.nio.file.Files
import java.nio.file.Path

/**
 * キーごとに 1 つの JSON ファイルを持ち、Gson でシリアライズする。
 *
 * [fallbackDirectory] を指定すると、プライマリのディレクトリにファイルが存在しない場合に限り、
 * そのディレクトリから読み込む。新しい保存は常に [directory] へ書き込む。
 */
class ShimeJsonStorage<T : Any>(
    override val name: String,
    private val directory: Path,
    private val type: Class<T>,
    private val fallbackDirectory: Path? = null
) : ShimeStorage<T> {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    init {
        require(name.isNotBlank()) { "Storage name must not be blank." }
        Files.createDirectories(directory)
    }

    override fun load(key: String): T? {
        val file = readFileFor(key) ?: return null
        try {
            return gson.fromJson(ShimeFiles.readText(file), type)
        } catch (exception: Exception) {
            throw ShimeStorageException("Could not load '$key' from JSON storage '$name'.", exception)
        }
    }

    override fun save(key: String, value: T) {
        try {
            ShimeFiles.atomicWrite(fileFor(key), gson.toJson(value, type))
        } catch (exception: Exception) {
            throw ShimeStorageException("Could not save '$key' to JSON storage '$name'.", exception)
        }
    }

    override fun delete(key: String): Boolean {
        return try {
            Files.deleteIfExists(fileFor(key))
        } catch (exception: Exception) {
            throw ShimeStorageException("Could not delete '$key' from JSON storage '$name'.", exception)
        }
    }

    override fun exists(key: String): Boolean = readFileFor(key) != null

    override fun keys(): Set<String> {
        val keys = LinkedHashSet<String>()
        keys += keysIn(directory)
        fallbackDirectory?.let { keys += keysIn(it) }
        return keys
    }

    override fun close() {
        // ファイルは操作ごとに開かれるため、永続的に閉じるものはない。
    }

    /** 読み込みに使う実在するファイルを返す。プライマリを優先し、なければフォールバックを見る。 */
    private fun readFileFor(key: String): Path? {
        val primary = fileFor(key)
        if (Files.exists(primary)) {
            return primary
        }
        return fallbackFileFor(key)?.takeIf { Files.exists(it) }
    }

    private fun keysIn(directory: Path): Set<String> {
        if (!Files.exists(directory)) {
            return emptySet()
        }
        return Files.list(directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".json") }
                .map { it.fileName.toString().removeSuffix(".json") }
                .toList()
                .toSet()
        }
    }

    private fun fileFor(key: String): Path {
        val safeKey = ShimeValidation.requireSafeKey(key, "storage key")
        return ShimeFiles.resolveWithin(directory, "$safeKey.json")
    }

    private fun fallbackFileFor(key: String): Path? {
        val fallback = fallbackDirectory ?: return null
        if (fallback == directory) {
            return null
        }
        val safeKey = ShimeValidation.requireSafeKey(key, "storage key")
        return ShimeFiles.resolveWithin(fallback, "$safeKey.json")
    }
}
