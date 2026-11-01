package shimeserver.shimepluginlibrary.shimedatabase

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import shimeserver.shimepluginlibrary.shimeexception.ShimeDatabaseException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.io.File
import java.math.BigDecimal
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShimeSqlDatabaseTest {
    @TempDir
    lateinit var tempDir: Path

    private val databases = ArrayList<ShimeSqlDatabase>()

    private fun database(): ShimeSqlDatabase {
        val file = File(tempDir.toFile(), "shime-test.db")
        val settings = ShimeDatabaseSettings.sqlite(file)
        val database = ShimeSqlDatabase(
            "test",
            settings,
            ShimeLogger(Logger.getLogger("ShimeSqlDatabaseTest"))
        )
        databases += database
        database.initialize()
        return database
    }

    @AfterEach
    fun closeDatabases() {
        databases.forEach { it.close() }
        databases.clear()
    }

    @Test
    fun `initializes and reports readiness`() {
        val database = database()

        assertTrue(database.isReady)
        assertTrue(database.healthCheck())
        assertEquals(ShimeDatabaseType.SQLITE, database.type)
        assertEquals("test", database.name)
    }

    @Test
    fun `creates rows and queries them with bound parameters`() {
        val database = database()
        database.update(
            "CREATE TABLE shime_player (id TEXT PRIMARY KEY, name TEXT NOT NULL, balance REAL NOT NULL)"
        )

        val uuid = UUID.randomUUID()
        val inserted = database.update(
            "INSERT INTO shime_player (id, name, balance) VALUES (?, ?, ?)",
            uuid,
            "ShimePlayer",
            BigDecimal("12.50")
        )
        assertEquals(1, inserted)

        val rows = database.query("SELECT id, name FROM shime_player WHERE name = ?", "ShimePlayer") { resultSet ->
            resultSet.getString("id") to resultSet.getString("name")
        }
        assertEquals(listOf(uuid.toString() to "ShimePlayer"), rows)
    }

    @Test
    fun `batch inserts all rows`() {
        val database = database()
        database.update("CREATE TABLE shime_value (id INTEGER PRIMARY KEY, label TEXT NOT NULL)")

        val counts = database.batch(
            "INSERT INTO shime_value (label) VALUES (?)",
            listOf(listOf("first"), listOf("second"), listOf("third"))
        )

        assertEquals(3, counts.size)
        assertEquals(3, database.query("SELECT label FROM shime_value") { it.getString(1) }.size)
    }

    @Test
    fun `transaction commits on success and rolls back on failure`() {
        val database = database()
        database.update("CREATE TABLE shime_account (id TEXT PRIMARY KEY, balance INTEGER NOT NULL)")

        database.transaction { connection ->
            connection.prepareStatement("INSERT INTO shime_account (id, balance) VALUES (?, ?)").use {
                it.setString(1, "committed")
                it.setInt(2, 10)
                it.executeUpdate()
            }
        }
        assertEquals(1, count(database, "committed"))

        assertFailsWith<ShimeDatabaseException> {
            database.transaction { connection ->
                connection.prepareStatement("INSERT INTO shime_account (id, balance) VALUES (?, ?)").use {
                    it.setString(1, "rolled-back")
                    it.setInt(2, 20)
                    it.executeUpdate()
                }
                throw IllegalStateException("force rollback")
            }
        }
        assertEquals(0, count(database, "rolled-back"))
        assertEquals(1, count(database, "committed"))
    }

    @Test
    fun `async helpers use the database worker pool`() {
        val database = database()
        database.update("CREATE TABLE shime_async (value INTEGER NOT NULL)")

        database.updateAsync("INSERT INTO shime_async (value) VALUES (?)", 42).get(5, TimeUnit.SECONDS)
        val values = database.queryAsync("SELECT value FROM shime_async") { it.getInt(1) }
            .get(5, TimeUnit.SECONDS)

        assertEquals(listOf(42), values)
    }

    @Test
    fun `rejects operations before initialization`() {
        val file = File(tempDir.toFile(), "not-initialized.db")
        val database = ShimeSqlDatabase(
            "closed",
            ShimeDatabaseSettings.sqlite(file),
            ShimeLogger(Logger.getLogger("ShimeSqlDatabaseTest"))
        )

        assertFalse(database.isReady)
        assertFailsWith<ShimeDatabaseException> {
            database.update("CREATE TABLE shime_none (id INTEGER)")
        }
        assertFailsWith<ShimeDatabaseException> {
            database.queryAsync("SELECT 1") { it.getInt(1) }
        }
        assertFalse(database.healthCheck())
    }

    @Test
    fun `rejects blank SQL`() {
        val database = database()
        assertFailsWith<ShimeDatabaseException> {
            database.query("   ") { it.getInt(1) }
        }
    }

    @Test
    fun `close is idempotent and stops accepting work`() {
        val database = database()
        database.close()
        database.close()

        assertFalse(database.isReady)
        assertFailsWith<ShimeDatabaseException> {
            database.update("CREATE TABLE shime_late (id INTEGER)")
        }
    }

    @Test
    fun `settings redact passwords and build the documented URLs`() {
        val mysql = ShimeDatabaseSettings.mysql(
            host = "db.example.com",
            database = "minecraft",
            username = "shime",
            password = "super-secret"
        )
        assertTrue(mysql.jdbcUrl.startsWith("jdbc:mysql://db.example.com:3306/minecraft?"))
        assertTrue(mysql.toString().contains("password='***'"))
        assertFalse(mysql.toString().contains("super-secret"))

        val sqlite = ShimeDatabaseSettings.sqlite(File(tempDir.toFile(), "url.db"))
        assertTrue(sqlite.jdbcUrl.startsWith("jdbc:sqlite:"))
        assertEquals("org.sqlite.JDBC", sqlite.driverClassName)
    }

    private fun count(database: ShimeSqlDatabase, id: String): Int {
        return database.query("SELECT COUNT(*) FROM shime_account WHERE id = ?", id) { it.getInt(1) }.single()
    }
}
