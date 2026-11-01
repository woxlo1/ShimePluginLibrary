package shimeserver.shimepluginlibrary.shimestorage

import com.google.gson.GsonBuilder
import shimeserver.shimepluginlibrary.shimeexception.ShimeStorageException
import shimeserver.shimepluginlibrary.shimeutil.ShimeFiles
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation
import java.nio.file.Files
import java.nio.file.Path

/** キーごとに 1 つの JSON ファイルを持ち、Gson でシリアライズする。 */
class ShimeJsonStorage<T : Any>(
    override val name: String,
    private val directory: Path,
    private val type: Class<T>
) : ShimeStorage<T> {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    init {
        require(name.isNotBlank()) { "Storage name must not be blank." }
        Files.createDirectories(directory)
    }

    override fun load(key: String): T? {
        val file = fileFor(key)
        if (!Files.exists(file)) {
            return null
        }
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

    override fun exists(key: String): Boolean = Files.exists(fileFor(key))

    override fun keys(): Set<String> {
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

    override fun close() {
        // ファイルは操作ごとに開かれるため、永続的に閉じるものはない。
    }

    private fun fileFor(key: String): Path {
        val safeKey = ShimeValidation.requireSafeKey(key, "storage key")
        return ShimeFiles.resolveWithin(directory, "$safeKey.json")
    }
}
