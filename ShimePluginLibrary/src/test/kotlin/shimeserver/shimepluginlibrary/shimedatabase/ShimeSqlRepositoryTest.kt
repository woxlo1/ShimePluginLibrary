package shimeserver.shimepluginlibrary.shimedatabase

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import shimeserver.shimepluginlibrary.shimeexception.ShimeOptimisticLockException
import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.io.File
import java.nio.file.Path
import java.sql.ResultSet
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ShimeSqlRepository] のページング、バッチ書き込み、楽観的ロックを SQLite 上で検証する。
 */
class ShimeSqlRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    private val databases = ArrayList<ShimeSqlDatabase>()

    private class TestPlayer(
        val id: String,
        var name: String,
        var score: Int,
        override var version: Long = 0
    ) : ShimeVersioned

    private class TestPlayerRepository(database: ShimeDatabase) : ShimeSqlRepository<TestPlayer, String>(
        database = database,
        name = "test-players",
        table = "test_players",
        idColumn = "id",
        columns = listOf("name", "score"),
        orderByColumn = "name",
        ascending = true,
        versionColumn = "version"
    ) {
        override fun mapRow(resultSet: ResultSet): TestPlayer {
            return TestPlayer(
                id = resultSet.getString("id"),
                name = resultSet.getString("name"),
                score = resultSet.getInt("score"),
                version = resultSet.getLong("version")
            )
        }

        override fun idOf(entity: TestPlayer): String = entity.id

        override fun valuesOf(entity: TestPlayer): List<Any?> = listOf(entity.name, entity.score)
    }

    private fun database(): ShimeSqlDatabase {
        val file = File(tempDir.toFile(), "repository-test.db")
        val database = ShimeSqlDatabase(
            name = "repository-test",
            settings = ShimeDatabaseSettings.sqlite(file),
            logger = ShimeLogger(Logger.getLogger("repository-test"))
        )
        database.initialize()
        database.update(
            """
            CREATE TABLE test_players (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                score INTEGER NOT NULL,
                version INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        databases += database
        return database
    }

    @AfterEach
    fun closeDatabases() {
        databases.forEach { it.close() }
        databases.clear()
    }

    @Test
    fun `保存したエンティティを主キーで取得できる`() {
        val repository = TestPlayerRepository(database())
        repository.save(TestPlayer("a", "Alice", 10))

        val loaded = repository.findById("a")
        assertNotNull(loaded)
        assertEquals("Alice", loaded.name)
        assertEquals(10, loaded.score)
        assertEquals(0L, loaded.version)
        assertNull(repository.findById("missing"))
    }

    @Test
    fun `更新時はバージョンが増加する`() {
        val repository = TestPlayerRepository(database())
        val player = repository.save(TestPlayer("a", "Alice", 10))

        player.score = 20
        repository.save(player)

        assertEquals(1L, player.version)
        val loaded = repository.findById("a")
        assertNotNull(loaded)
        assertEquals(20, loaded.score)
        assertEquals(1L, loaded.version)
    }

    @Test
    fun `古いバージョンでの更新は楽観的ロック例外になる`() {
        val repository = TestPlayerRepository(database())
        repository.save(TestPlayer("a", "Alice", 10))
        val first = repository.findById("a")!!
        val second = repository.findById("a")!!

        first.score = 20
        repository.save(first)

        second.score = 30
        assertFailsWith<ShimeOptimisticLockException> {
            repository.save(second)
        }
        assertEquals(20, repository.findById("a")!!.score)
    }

    @Test
    fun `ページングは件数と内容を返す`() {
        val repository = TestPlayerRepository(database())
        repository.saveAll(
            listOf(
                TestPlayer("a", "Alice", 1),
                TestPlayer("b", "Bob", 2),
                TestPlayer("c", "Carol", 3),
                TestPlayer("d", "Dave", 4),
                TestPlayer("e", "Eve", 5)
            )
        )

        val first = repository.findPage(ShimePageRequest(page = 0, size = 2))
        assertEquals(5L, first.totalElements)
        assertEquals(3, first.totalPages)
        assertEquals(listOf("Alice", "Bob"), first.content.map { it.name })
        assertTrue(first.hasNext)
        assertFalse(first.hasPrevious)

        val last = repository.findPage(ShimePageRequest(page = 2, size = 2))
        assertEquals(listOf("Eve"), last.content.map { it.name })
        assertFalse(last.hasNext)

        val beyond = repository.findPage(ShimePageRequest(page = 10, size = 2))
        assertTrue(beyond.isEmpty)
        assertEquals(5L, beyond.totalElements)
    }

    @Test
    fun `バッチ挿入は全件を書き込む`() {
        val repository = TestPlayerRepository(database())
        val inserted = repository.insertAll(
            listOf(
                TestPlayer("a", "Alice", 1),
                TestPlayer("b", "Bob", 2),
                TestPlayer("c", "Carol", 3)
            )
        )

        assertEquals(3, inserted)
        assertEquals(3L, repository.count())
        assertTrue(repository.existsById("b"))
        assertFalse(repository.existsById("missing"))
    }

    @Test
    fun `saveAllはトランザクション内で全件を保存する`() {
        val repository = TestPlayerRepository(database())
        val count = repository.saveAll(
            listOf(
                TestPlayer("a", "Alice", 1),
                TestPlayer("b", "Bob", 2)
            )
        )

        assertEquals(2, count)
        assertEquals(2L, repository.count())
        assertEquals(listOf("Alice", "Bob"), repository.findAll().map { it.name })
    }

    @Test
    fun `削除と統計取得が動作する`() {
        val database = database()
        val repository = TestPlayerRepository(database)
        repository.save(TestPlayer("a", "Alice", 1))

        assertTrue(repository.deleteById("a"))
        assertFalse(repository.deleteById("a"))

        val stats = database.stats()
        assertTrue(stats.updates > 0)
        assertTrue(stats.transactions > 0)
        assertTrue(stats.totalOperations > 0)
        assertEquals(0L, stats.failures)
    }

    @Test
    fun `不正な識別子はリポジトリ生成時に拒否される`() {
        val database = database()
        assertFailsWith<ShimeValidationException> {
            object : ShimeSqlRepository<TestPlayer, String>(
                database = database,
                name = "invalid",
                table = "players; DROP TABLE test_players",
                idColumn = "id",
                columns = listOf("name")
            ) {
                override fun mapRow(resultSet: ResultSet): TestPlayer = TestPlayer("", "", 0)
                override fun idOf(entity: TestPlayer): String = entity.id
                override fun valuesOf(entity: TestPlayer): List<Any?> = emptyList()
            }
        }
    }
}
