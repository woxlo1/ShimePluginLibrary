package shimeserver.shimepluginlibrary.shimestorage

import org.bukkit.configuration.file.YamlConfiguration
import shimeserver.shimepluginlibrary.shimeexception.ShimeStorageException
import shimeserver.shimepluginlibrary.shimeutil.ShimeFiles
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation
import java.nio.file.Files
import java.nio.file.Path

/** キーごとに 1 つの YAML ファイル。小〜中規模のレコードに適している。 */
class ShimeYamlStorage<T : Any>(
    override val name: String,
    private val directory: Path,
    private val codec: ShimeStorageCodec<T>
) : ShimeStorage<T> {
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
            val yaml = YamlConfiguration.loadConfiguration(file.toFile())
            @Suppress("UNCHECKED_CAST")
            val values = yaml.getValues(false) as Map<String, Any?>
            return codec.decode(values)
        } catch (exception: Exception) {
            throw ShimeStorageException("Could not load '$key' from YAML storage '$name'.", exception)
        }
    }

    override fun save(key: String, value: T) {
        try {
            val yaml = YamlConfiguration()
            codec.encode(value).forEach { (path, entry) -> yaml.set(path, entry) }
            ShimeFiles.atomicWrite(fileFor(key), yaml.saveToString())
        } catch (exception: Exception) {
            throw ShimeStorageException("Could not save '$key' to YAML storage '$name'.", exception)
        }
    }

    override fun delete(key: String): Boolean {
        return try {
            Files.deleteIfExists(fileFor(key))
        } catch (exception: Exception) {
            throw ShimeStorageException("Could not delete '$key' from YAML storage '$name'.", exception)
        }
    }

    override fun exists(key: String): Boolean = Files.exists(fileFor(key))

    override fun keys(): Set<String> {
        if (!Files.exists(directory)) {
            return emptySet()
        }
        return Files.list(directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".yml") }
                .map { it.fileName.toString().removeSuffix(".yml") }
                .toList()
                .toSet()
        }
    }

    override fun close() {
        // ファイルは操作ごとに開かれるため、永続的に閉じるものはない。
    }

    private fun fileFor(key: String): Path {
        val safeKey = ShimeValidation.requireSafeKey(key, "storage key")
        return ShimeFiles.resolveWithin(directory, "$safeKey.yml")
    }
}
