package shimeserver.shimepluginlibrary.shimedatabase

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import shimeserver.shimepluginlibrary.shimecache.ShimeMemoryCache
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShimeDatabaseAdvancedTest {
    @TempDir
    lateinit var tempDir: Path

    private val databases = ArrayList<ShimeSqlDatabase>()
    private val managers = ArrayList<ShimeDatabaseManager>()

    private fun database(name: String = "test"): ShimeSqlDatabase {
        val file = File(tempDir.toFile(), "$name.db")
        val database = ShimeSqlDatabase(
            name,
            ShimeDatabaseSettings.sqlite(file),
            ShimeLogger(Logger.getLogger("ShimeDatabaseAdvancedTest"))
        )
        databases += database
        database.initialize()
        return database
    }

    private fun manager(): ShimeDatabaseManager {
        val manager = ShimeDatabaseManager(ShimeLogger(Logger.getLogger("ShimeDatabaseAdvancedTest")))
        managers += manager
        return manager
    }

    @AfterEach
    fun closeAll() {
        managers.forEach { it.closeAll() }
        managers.clear()
        databases.forEach { it.close() }
        databases.clear()
    }

    @Test
    fun `schema validation reports existing and missing columns`() {
        val database = database()
        database.update("CREATE TABLE shime_player (id TEXT PRIMARY KEY, name TEXT NOT NULL)")

        val schema = ShimeSchemaManager(database)
        val valid = schema.validate(
            listOf(ShimeTableSpec("shime_player", setOf("id", "name")))
        )
        assertTrue(valid.isValid)
        assertEquals(listOf("shime_player"), valid.checkedTables)

        val invalid = schema.validate(
            listOf(
                ShimeTableSpec("shime_player", setOf("id", "level")),
                ShimeTableSpec("shime_missing", setOf("id"))
            )
        )
        assertFalse(invalid.isValid)
        assertEquals(setOf("shime_player", "shime_missing"), invalid.invalidTables())
        assertTrue(invalid.issues.any { it.type == ShimeSchemaIssueType.MISSING_COLUMN && it.column == "level" })
        assertTrue(invalid.issues.any { it.type == ShimeSchemaIssueType.MISSING_TABLE })
        assertTrue(invalid.summary().contains("shime_missing"))
    }

    @Test
    fun `schema validation is case insensitive for table and column names`() {
        val database = database()
        database.update("CREATE TABLE shime_Widget (ID TEXT PRIMARY KEY, Label TEXT NOT NULL)")

        val schema = ShimeSchemaManager(database)
        val report = schema.validate(
            listOf(ShimeTableSpec("shime_widget", setOf("id", "label")))
        )

        assertTrue(report.isValid, report.summary())
    }

    @Test
    fun `integrity check verifies expected tables and sqlite pragmas`() {
        val database = database()
        database.update("CREATE TABLE shime_value (id INTEGER PRIMARY KEY, label TEXT NOT NULL)")

        val schema = ShimeSchemaManager(database)
        val healthy = schema.integrityCheck(listOf("shime_value"))
        assertTrue(healthy.isHealthy, healthy.summary())
        assertTrue(healthy.checks.any { it.name == "sqlite:integrity_check" && it.passed })

        val missing = schema.integrityCheck(listOf("shime_absent"))
        assertFalse(missing.isHealthy)
        assertEquals(1, missing.failedChecks().size)
        assertTrue(missing.summary().contains("shime_absent"))
    }

    @Test
    fun `cached repository serves reads from cache and invalidates on write`() {
        val delegate = InMemoryWidgetRepository()
        val cache = ShimeMemoryCache<String, Widget>()
        val repository = ShimeCachedRepository(delegate, cache, keyExtractor = { it.id })

        delegate.values["w1"] = Widget("w1", 1)
        assertEquals(Widget("w1", 1), repository.findById("w1"))
        assertEquals(Widget("w1", 1), repository.findById("w1"))
        assertEquals(1, delegate.findByIdCalls.get())

        repository.save(Widget("w1", 2))
        assertEquals(Widget("w1", 2), repository.findById("w1"))
        assertEquals(1, delegate.findByIdCalls.get())

        assertTrue(repository.invalidate("w1"))
        assertFalse(repository.invalidate("w1"))
        assertEquals(Widget("w1", 2), repository.findById("w1"))
        assertEquals(2, delegate.findByIdCalls.get())

        delegate.values["w1"] = Widget("w1", 3)
        assertTrue(repository.deleteById("w1"))
        assertNull(repository.findById("w1"))
        assertFalse(repository.existsById("w1"))
    }

    @Test
    fun `database manager exposes health reports without leaking credentials`() {
        val manager = manager()
        val file = File(tempDir.toFile(), "managed.db")
        manager.connect("main", ShimeDatabaseSettings.sqlite(file))

        val reports = manager.healthReports()
        assertEquals(1, reports.size)
        val report = reports.single()
        assertEquals("main", report.name)
        assertEquals(ShimeDatabaseType.SQLITE, report.type)
        assertTrue(report.ready)
        assertTrue(report.healthy)
        assertTrue(manager.isHealthy())
        assertTrue(report.summary().contains("main"))
        assertFalse(report.summary().contains("password"))

        assertNotNull(manager.healthReport("main"))
        assertNull(manager.healthReport("missing"))
    }

    @Test
    fun `integrity report marks an unavailable database as unhealthy`() {
        val report = ShimeDatabaseIntegrityReport(
            databaseName = "offline",
            checks = listOf(
                ShimeDatabaseIntegrityCheck("connection", false, "Database health check failed.")
            )
        )

        assertFalse(report.isHealthy)
        assertEquals(1, report.failedChecks().size)
        assertTrue(report.summary().startsWith("connection:"))
    }

    private data class Widget(val id: String, val value: Int)

    private class InMemoryWidgetRepository : ShimeRepository<Widget, String> {
        val values = LinkedHashMap<String, Widget>()
        val findByIdCalls = AtomicInteger()

        override val name: String = "widgets"

        override fun findById(id: String): Widget? {
            findByIdCalls.incrementAndGet()
            return values[id]
        }

        override fun findAll(): List<Widget> = values.values.toList()

        override fun findPage(request: ShimePageRequest): ShimePage<Widget> {
            val all = findAll()
            return ShimePage(
                content = all.drop(request.offset.toInt()).take(request.size),
                page = request.page,
                size = request.size,
                totalElements = all.size.toLong()
            )
        }

        override fun save(entity: Widget): Widget {
            values[entity.id] = entity
            return entity
        }

        override fun saveAll(entities: List<Widget>): Int {
            entities.forEach { values[it.id] = it }
            return entities.size
        }

        override fun deleteById(id: String): Boolean = values.remove(id) != null

        override fun count(): Long = values.size.toLong()
    }
}
