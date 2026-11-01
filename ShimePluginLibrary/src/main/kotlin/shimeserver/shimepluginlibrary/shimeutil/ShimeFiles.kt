package shimeserver.shimepluginlibrary.shimeutil

import shimeserver.shimepluginlibrary.shimeexception.ShimeStorageException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** パストラバーサル対策とアトミック書き込みを備えたファイルヘルパー。 */
object ShimeFiles {
    private val BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun resolveWithin(root: Path, relativePath: String): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val resolved = normalizedRoot.resolve(relativePath).normalize()
        if (!resolved.startsWith(normalizedRoot)) {
            throw ShimeStorageException("Path '$relativePath' escapes the storage root.")
        }
        return resolved
    }

    fun atomicWrite(file: Path, content: String) {
        try {
            val parent = file.toAbsolutePath().parent
            Files.createDirectories(parent)
            val temporary = Files.createTempFile(parent, file.fileName.toString(), ".tmp")
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8)
                try {
                    Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        } catch (exception: IOException) {
            throw ShimeStorageException("Could not write '${file.fileName}'.", exception)
        }
    }

    fun backup(file: Path, backupDirectory: Path): Path? {
        if (!Files.exists(file)) {
            return null
        }
        try {
            Files.createDirectories(backupDirectory)
            val stamp = LocalDateTime.now().format(BACKUP_TIMESTAMP)
            val backup = backupDirectory.resolve("${file.fileName}.$stamp.bak")
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING)
            return backup
        } catch (exception: IOException) {
            throw ShimeStorageException("Could not create a backup for '${file.fileName}'.", exception)
        }
    }

    fun readText(file: Path): String {
        try {
            return Files.readString(file, StandardCharsets.UTF_8)
        } catch (exception: IOException) {
            throw ShimeStorageException("Could not read '${file.fileName}'.", exception)
        }
    }
}
