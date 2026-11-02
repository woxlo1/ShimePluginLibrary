package shimeserver.shimepluginlibrary.shimedatabase

import shimeserver.shimepluginlibrary.shimeexception.ShimeDatabaseException
import shimeserver.shimepluginlibrary.shimeexception.ShimeOptimisticLockException
import shimeserver.shimepluginlibrary.shimevalidation.ShimeValidation
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

/**
 * 単一テーブル向けの汎用 SQL リポジトリ。
 *
 * サブクラスは行のマッピングと値の列挙だけを実装します。SQL のテーブル名、列名、並び順はコンストラクターで
 * 受け取り、識別子として検証したうえで固定テンプレートへ埋め込みます。値は必ずパラメーターとして
 * バインドされるため、呼び出し側の入力が SQL 構文へ混入することはありません。
 *
 * [versionColumn] を指定した場合、エンティティは [ShimeVersioned] を実装しなければなりません。
 * 更新はバージョン一致時のみ成功し、不一致なら [ShimeOptimisticLockException] がスローされます。
 *
 * @param columns 主キーとバージョン列を除く、更新対象の列。順序は [valuesOf] の返却順と一致させること。
 * @param orderByColumn 一覧取得時の並び替え列。null の場合はデータベース既定の順序。
 * @param ascending 並び替えを昇順にするか。
 */
abstract class ShimeSqlRepository<T : Any, ID : Any>(
    protected val database: ShimeDatabase,
    override val name: String,
    protected val table: String,
    protected val idColumn: String,
    protected val columns: List<String>,
    protected val orderByColumn: String? = null,
    protected val ascending: Boolean = true,
    protected val versionColumn: String? = null
) : ShimeRepository<T, ID> {

    init {
        ShimeValidation.requireNonBlank(name, "repository name")
        ShimeValidation.requireSafeKey(table, "repository table")
        ShimeValidation.requireSafeKey(idColumn, "repository id column")
        require(columns.isNotEmpty()) { "A repository must expose at least one column." }
        columns.forEach { ShimeValidation.requireSafeKey(it, "repository column") }
        require(columns.none { it == idColumn }) { "The id column must not be repeated in the value columns." }
        require(columns.distinct().size == columns.size) { "Repository columns must be unique." }
        versionColumn?.let { version ->
            ShimeValidation.requireSafeKey(version, "repository version column")
            require(version != idColumn) { "The version column must differ from the id column." }
            require(version !in columns) { "The version column must not be listed in the value columns." }
        }
        orderByColumn?.let { order ->
            ShimeValidation.requireSafeKey(order, "repository order column")
            require(order == idColumn || order == versionColumn || order in columns) {
                "The order column '$order' must be the id column, the version column, or a value column."
            }
        }
    }

    private val selectSql: String = buildString {
        append("SELECT ").append(idColumn)
        columns.forEach { append(", ").append(it) }
        versionColumn?.let { append(", ").append(it) }
        append(" FROM ").append(table)
    }

    private val orderSql: String = orderByColumn?.let {
        " ORDER BY $it ${if (ascending) "ASC" else "DESC"}"
    }.orEmpty()

    private val insertSql: String = buildString {
        append("INSERT INTO ").append(table).append(" (").append(idColumn)
        columns.forEach { append(", ").append(it) }
        versionColumn?.let { append(", ").append(it) }
        append(") VALUES (").append(List(columns.size + 1 + if (versionColumn == null) 0 else 1) { "?" }.joinToString(", "))
        append(")")
    }

    private val updateSql: String = buildString {
        append("UPDATE ").append(table).append(" SET ")
        append(columns.joinToString(", ") { "$it = ?" })
        versionColumn?.let { append(", ").append(it).append(" = ?") }
        append(" WHERE ").append(idColumn).append(" = ?")
        versionColumn?.let { append(" AND ").append(it).append(" = ?") }
    }

    /** 1 行をドメインオブジェクトへ変換する。 */
    protected abstract fun mapRow(resultSet: ResultSet): T

    /** エンティティの主キーを返す。 */
    protected abstract fun idOf(entity: T): ID

    /**
     * [columns] と同じ順序・同じ件数の値を返す。
     *
     * バージョン列は含めないこと。バージョンはリポジトリが管理します。
     */
    protected abstract fun valuesOf(entity: T): List<Any?>

    final override fun findById(id: ID): T? {
        return database.query("$selectSql WHERE $idColumn = ?", id) { mapRow(it) }.firstOrNull()
    }

    final override fun findAll(): List<T> {
        return database.query("$selectSql$orderSql") { mapRow(it) }
    }

    final override fun findPage(request: ShimePageRequest): ShimePage<T> {
        val total = count()
        if (total == 0L || request.offset >= total) {
            return ShimePage(emptyList(), request.page, request.size, total)
        }
        val content = database.query(
            "$selectSql$orderSql LIMIT ? OFFSET ?",
            request.size,
            request.offset
        ) { mapRow(it) }
        return ShimePage(content, request.page, request.size, total)
    }

    final override fun count(): Long {
        return database.query("SELECT COUNT(*) FROM $table") { it.getLong(1) }.firstOrNull() ?: 0L
    }

    final override fun existsById(id: ID): Boolean {
        return database.query("SELECT 1 FROM $table WHERE $idColumn = ?", id) { true }.isNotEmpty()
    }

    final override fun deleteById(id: ID): Boolean {
        return database.update("DELETE FROM $table WHERE $idColumn = ?", id) > 0
    }

    /**
     * 1 件を保存する。
     *
     * 既存行がある場合は更新し、なければ挿入します。バージョン列がある場合、更新と存在確認は同じ
     * トランザクション内で実行されます。
     */
    final override fun save(entity: T): T {
        database.transaction { connection ->
            saveWithConnection(connection, entity)
        }
        return entity
    }

    /**
     * 複数件を 1 つのトランザクションで保存する。
     *
     * 途中で失敗した場合は全件がロールバックされます。
     */
    final override fun saveAll(entities: List<T>): Int {
        if (entities.isEmpty()) {
            return 0
        }
        return database.transaction { connection ->
            var affected = 0
            entities.forEach { entity ->
                affected += saveWithConnection(connection, entity)
            }
            affected
        }
    }

    /**
     * 新規行だけを 1 回のバッチで挿入する。
     *
     * 既存の主キーと衝突した場合はデータベースが例外を投げ、トランザクションは失敗します。既存行の更新も
     * 必要な場合は [saveAll] を使用してください。
     *
     * @return 挿入に成功した件数。
     */
    fun insertAll(entities: List<T>): Int {
        if (entities.isEmpty()) {
            return 0
        }
        val parameters = entities.map { insertParameters(it) }
        val results = database.batch(insertSql, parameters)
        return results.count { it >= 0 || it == Statement.SUCCESS_NO_INFO }
    }

    private fun saveWithConnection(connection: Connection, entity: T): Int {
        val id = idOf(entity)
        val versioned = versionColumn != null
        if (versioned && entity !is ShimeVersioned) {
            throw ShimeDatabaseException(
                "Repository '$name' uses a version column, but '${entity.javaClass.name}' does not implement ShimeVersioned."
            )
        }
        val values = valuesOf(entity)
        if (values.size != columns.size) {
            throw ShimeDatabaseException(
                "Repository '$name' expected ${columns.size} values but '${entity.javaClass.name}' supplied ${values.size}."
            )
        }

        val updated = connection.prepareStatement(updateSql).use { statement ->
            val updateParameters = ArrayList<Any?>(values.size + 3)
            updateParameters.addAll(values)
            if (versioned) {
                updateParameters.add((entity as ShimeVersioned).version + 1L)
            }
            updateParameters.add(id)
            if (versioned) {
                updateParameters.add((entity as ShimeVersioned).version)
            }
            ShimeSqlBinder.bind(statement, updateParameters)
            statement.executeUpdate()
        }
        if (updated > 0) {
            if (versioned) {
                (entity as ShimeVersioned).version += 1L
            }
            return updated
        }
        if (versioned && existsWithConnection(connection, id)) {
            throw ShimeOptimisticLockException(
                "Repository '$name' detected a concurrent modification of '${id}'."
            )
        }
        return connection.prepareStatement(insertSql).use { statement ->
            ShimeSqlBinder.bind(statement, insertParameters(entity))
            statement.executeUpdate()
        }
    }

    private fun insertParameters(entity: T): List<Any?> {
        val values = valuesOf(entity)
        if (values.size != columns.size) {
            throw ShimeDatabaseException(
                "Repository '$name' expected ${columns.size} values but '${entity.javaClass.name}' supplied ${values.size}."
            )
        }
        val parameters = ArrayList<Any?>(values.size + 2)
        parameters.add(idOf(entity))
        parameters.addAll(values)
        versionColumn?.let {
            val versioned = entity as? ShimeVersioned
                ?: throw ShimeDatabaseException(
                    "Repository '$name' uses a version column, but '${entity.javaClass.name}' does not implement ShimeVersioned."
                )
            parameters.add(versioned.version)
        }
        return parameters
    }

    private fun existsWithConnection(connection: Connection, id: ID): Boolean {
        return connection.prepareStatement("SELECT 1 FROM $table WHERE $idColumn = ?").use { statement ->
            ShimeSqlBinder.bind(statement, listOf(id))
            statement.executeQuery().use { it.next() }
        }
    }
}
