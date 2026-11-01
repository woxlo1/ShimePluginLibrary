package shimeserver.shimepluginlibrary.shimeeconomy

import shimeserver.shimepluginlibrary.shimedatabase.ShimeDatabase
import shimeserver.shimepluginlibrary.shimedatabase.ShimeDatabaseType
import shimeserver.shimepluginlibrary.shimeexception.ShimeDatabaseException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * データベースを基盤とする経済システム。残高変更はトランザクション内で行われ、監査証跡を保持します。
 *
 * MySQL/MariaDB では行を `SELECT ... FOR UPDATE` でロックします。SQLite ではシリアライズされた
 * 書き込みモードに依存します（データベースプールは 1 接続に制限されています）。
 */
class ShimeDatabaseEconomyService(
    private val database: ShimeDatabase,
    override val currency: ShimeCurrency,
    private val logger: ShimeLogger
) : ShimeEconomyService {
    private val accountsTable = "shime_economy_accounts"
    private val transactionsTable = "shime_economy_transactions"
    private val amountType = "DECIMAL(19,${currency.scale.coerceAtLeast(4)})"

    fun initialize() {
        database.update(
            "CREATE TABLE IF NOT EXISTS $accountsTable (" +
                "account_uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
                "balance $amountType NOT NULL)"
        )
        database.update(
            "CREATE TABLE IF NOT EXISTS $transactionsTable (" +
                "transaction_id VARCHAR(36) NOT NULL, " +
                "account_uuid VARCHAR(36) NOT NULL, " +
                "type VARCHAR(32) NOT NULL, " +
                "amount $amountType NOT NULL, " +
                "balance_after $amountType NOT NULL, " +
                "reason VARCHAR(255), " +
                "created_at BIGINT NOT NULL)"
        )
        database.update(
            "CREATE INDEX IF NOT EXISTS idx_shime_economy_account ON $transactionsTable (account_uuid, created_at)"
        )
        logger.info(ShimeLogCategory.ECONOMY, "Database economy tables are ready.")
    }

    override fun balance(account: UUID): BigDecimal {
        val rows = database.query(
            "SELECT balance FROM $accountsTable WHERE account_uuid = ?",
            account.toString()
        ) { resultSet -> resultSet.getBigDecimal(1) }
        return rows.firstOrNull()?.setScale(currency.scale, RoundingMode.HALF_UP)
            ?: BigDecimal.ZERO.setScale(currency.scale)
    }

    override fun setBalance(account: UUID, amount: BigDecimal, reason: String?): ShimeTransactionResult {
        val normalized = normalizeNonNegative(amount)
            ?: return ShimeTransactionResult.failure("Amount must not be negative.")
        return database.transaction { connection ->
            ensureAccount(connection, account)
            val current = selectBalance(connection, account, lock = true)
            updateBalance(connection, account, normalized)
            val transactionId = UUID.randomUUID().toString()
            insertRecord(connection, transactionId, account, ShimeTransactionType.SET, normalized, normalized, reason)
            ShimeTransactionResult.success(transactionId, normalized)
        }
    }

    override fun deposit(account: UUID, amount: BigDecimal, reason: String?): ShimeTransactionResult {
        val normalized = normalizePositive(amount)
            ?: return ShimeTransactionResult.failure("Amount must be positive.")
        return database.transaction { connection ->
            ensureAccount(connection, account)
            val current = selectBalance(connection, account, lock = true)
            val updated = current.add(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
            updateBalance(connection, account, updated)
            val transactionId = UUID.randomUUID().toString()
            insertRecord(connection, transactionId, account, ShimeTransactionType.DEPOSIT, normalized, updated, reason)
            ShimeTransactionResult.success(transactionId, updated)
        }
    }

    override fun withdraw(account: UUID, amount: BigDecimal, reason: String?): ShimeTransactionResult {
        val normalized = normalizePositive(amount)
            ?: return ShimeTransactionResult.failure("Amount must be positive.")
        return database.transaction { connection ->
            ensureAccount(connection, account)
            val current = selectBalance(connection, account, lock = true)
            if (current < normalized) {
                return@transaction ShimeTransactionResult.failure("Insufficient balance.")
            }
            val updated = current.subtract(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
            updateBalance(connection, account, updated)
            val transactionId = UUID.randomUUID().toString()
            insertRecord(connection, transactionId, account, ShimeTransactionType.WITHDRAW, normalized, updated, reason)
            ShimeTransactionResult.success(transactionId, updated)
        }
    }

    override fun transfer(from: UUID, to: UUID, amount: BigDecimal, reason: String?): ShimeTransactionResult {
        if (from == to) {
            return ShimeTransactionResult.failure("Sender and receiver must be different accounts.")
        }
        val normalized = normalizePositive(amount)
            ?: return ShimeTransactionResult.failure("Amount must be positive.")
        return database.transaction { connection ->
            // 逆方向の送金同士でデッドロックしないよう、アカウントを決まった順序でロックします。
            val first = minOf(from.toString(), to.toString())
            val second = maxOf(from.toString(), to.toString())
            ensureAccount(connection, UUID.fromString(first))
            ensureAccount(connection, UUID.fromString(second))
            val fromBalance = selectBalance(connection, from, lock = true)
            val toBalance = selectBalance(connection, to, lock = true)
            if (fromBalance < normalized) {
                return@transaction ShimeTransactionResult.failure("Insufficient balance.")
            }
            val fromUpdated = fromBalance.subtract(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
            val toUpdated = toBalance.add(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
            updateBalance(connection, from, fromUpdated)
            updateBalance(connection, to, toUpdated)
            val transactionId = UUID.randomUUID().toString()
            insertRecord(connection, transactionId, from, ShimeTransactionType.TRANSFER_OUT, normalized, fromUpdated, reason)
            insertRecord(connection, transactionId, to, ShimeTransactionType.TRANSFER_IN, normalized, toUpdated, reason)
            ShimeTransactionResult.success(transactionId, fromUpdated)
        }
    }

    override fun history(account: UUID, limit: Int): List<ShimeTransactionRecord> {
        require(limit > 0) { "History limit must be positive." }
        return database.query(
            "SELECT transaction_id, account_uuid, type, amount, balance_after, reason, created_at " +
                "FROM $transactionsTable WHERE account_uuid = ? ORDER BY created_at DESC LIMIT ?",
            account.toString(),
            limit
        ) { resultSet ->
            ShimeTransactionRecord(
                transactionId = resultSet.getString("transaction_id"),
                account = UUID.fromString(resultSet.getString("account_uuid")),
                type = ShimeTransactionType.valueOf(resultSet.getString("type")),
                amount = resultSet.getBigDecimal("amount"),
                balanceAfter = resultSet.getBigDecimal("balance_after"),
                reason = resultSet.getString("reason"),
                timestamp = resultSet.getLong("created_at")
            )
        }
    }

    override fun balanceAsync(account: UUID): CompletableFuture<BigDecimal> {
        return database.queryAsync(
            "SELECT balance FROM $accountsTable WHERE account_uuid = ?",
            account.toString()
        ) { resultSet -> resultSet.getBigDecimal(1) }.thenApply { rows ->
            rows.firstOrNull()?.setScale(currency.scale, RoundingMode.HALF_UP)
                ?: BigDecimal.ZERO.setScale(currency.scale)
        }
    }

    override fun depositAsync(
        account: UUID,
        amount: BigDecimal,
        reason: String?
    ): CompletableFuture<ShimeTransactionResult> {
        return database.transactionAsync { connection -> depositInTransaction(connection, account, amount, reason) }
    }

    override fun withdrawAsync(
        account: UUID,
        amount: BigDecimal,
        reason: String?
    ): CompletableFuture<ShimeTransactionResult> {
        return database.transactionAsync { connection -> withdrawInTransaction(connection, account, amount, reason) }
    }

    override fun transferAsync(
        from: UUID,
        to: UUID,
        amount: BigDecimal,
        reason: String?
    ): CompletableFuture<ShimeTransactionResult> {
        return database.transactionAsync { connection -> transferInTransaction(connection, from, to, amount, reason) }
    }

    private fun depositInTransaction(
        connection: Connection,
        account: UUID,
        amount: BigDecimal,
        reason: String?
    ): ShimeTransactionResult {
        val normalized = normalizePositive(amount)
            ?: return ShimeTransactionResult.failure("Amount must be positive.")
        ensureAccount(connection, account)
        val current = selectBalance(connection, account, lock = true)
        val updated = current.add(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
        updateBalance(connection, account, updated)
        val transactionId = UUID.randomUUID().toString()
        insertRecord(connection, transactionId, account, ShimeTransactionType.DEPOSIT, normalized, updated, reason)
        return ShimeTransactionResult.success(transactionId, updated)
    }

    private fun withdrawInTransaction(
        connection: Connection,
        account: UUID,
        amount: BigDecimal,
        reason: String?
    ): ShimeTransactionResult {
        val normalized = normalizePositive(amount)
            ?: return ShimeTransactionResult.failure("Amount must be positive.")
        ensureAccount(connection, account)
        val current = selectBalance(connection, account, lock = true)
        if (current < normalized) {
            return ShimeTransactionResult.failure("Insufficient balance.")
        }
        val updated = current.subtract(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
        updateBalance(connection, account, updated)
        val transactionId = UUID.randomUUID().toString()
        insertRecord(connection, transactionId, account, ShimeTransactionType.WITHDRAW, normalized, updated, reason)
        return ShimeTransactionResult.success(transactionId, updated)
    }

    private fun transferInTransaction(
        connection: Connection,
        from: UUID,
        to: UUID,
        amount: BigDecimal,
        reason: String?
    ): ShimeTransactionResult {
        if (from == to) {
            return ShimeTransactionResult.failure("Sender and receiver must be different accounts.")
        }
        val normalized = normalizePositive(amount)
            ?: return ShimeTransactionResult.failure("Amount must be positive.")
        val first = minOf(from.toString(), to.toString())
        val second = maxOf(from.toString(), to.toString())
        ensureAccount(connection, UUID.fromString(first))
        ensureAccount(connection, UUID.fromString(second))
        val fromBalance = selectBalance(connection, from, lock = true)
        val toBalance = selectBalance(connection, to, lock = true)
        if (fromBalance < normalized) {
            return ShimeTransactionResult.failure("Insufficient balance.")
        }
        val fromUpdated = fromBalance.subtract(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
        val toUpdated = toBalance.add(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
        updateBalance(connection, from, fromUpdated)
        updateBalance(connection, to, toUpdated)
        val transactionId = UUID.randomUUID().toString()
        insertRecord(connection, transactionId, from, ShimeTransactionType.TRANSFER_OUT, normalized, fromUpdated, reason)
        insertRecord(connection, transactionId, to, ShimeTransactionType.TRANSFER_IN, normalized, toUpdated, reason)
        return ShimeTransactionResult.success(transactionId, fromUpdated)
    }

    private fun ensureAccount(connection: Connection, account: UUID) {
        val sql = if (database.type == ShimeDatabaseType.SQLITE) {
            "INSERT OR IGNORE INTO $accountsTable (account_uuid, balance) VALUES (?, ?)"
        } else {
            "INSERT IGNORE INTO $accountsTable (account_uuid, balance) VALUES (?, ?)"
        }
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, account.toString())
            statement.setBigDecimal(2, BigDecimal.ZERO.setScale(currency.scale))
            statement.executeUpdate()
        }
    }

    private fun selectBalance(connection: Connection, account: UUID, lock: Boolean): BigDecimal {
        val suffix = if (lock && database.type != ShimeDatabaseType.SQLITE) " FOR UPDATE" else ""
        connection.prepareStatement(
            "SELECT balance FROM $accountsTable WHERE account_uuid = ?$suffix"
        ).use { statement ->
            statement.setString(1, account.toString())
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    throw ShimeDatabaseException("Economy account '$account' disappeared during a transaction.")
                }
                return resultSet.getBigDecimal(1).setScale(currency.scale, RoundingMode.HALF_UP)
            }
        }
    }

    private fun updateBalance(connection: Connection, account: UUID, balance: BigDecimal) {
        connection.prepareStatement(
            "UPDATE $accountsTable SET balance = ? WHERE account_uuid = ?"
        ).use { statement ->
            statement.setBigDecimal(1, balance)
            statement.setString(2, account.toString())
            statement.executeUpdate()
        }
    }

    private fun insertRecord(
        connection: Connection,
        transactionId: String,
        account: UUID,
        type: ShimeTransactionType,
        amount: BigDecimal,
        balanceAfter: BigDecimal,
        reason: String?
    ) {
        connection.prepareStatement(
            "INSERT INTO $transactionsTable " +
                "(transaction_id, account_uuid, type, amount, balance_after, reason, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setString(1, transactionId)
            statement.setString(2, account.toString())
            statement.setString(3, type.name)
            statement.setBigDecimal(4, amount)
            statement.setBigDecimal(5, balanceAfter)
            statement.setString(6, reason?.take(255))
            statement.setLong(7, System.currentTimeMillis())
            statement.executeUpdate()
        }
    }

    private fun normalizePositive(amount: BigDecimal): BigDecimal? {
        if (amount <= BigDecimal.ZERO) {
            return null
        }
        val normalized = amount.setScale(currency.scale, RoundingMode.HALF_UP)
        return if (normalized <= BigDecimal.ZERO) null else normalized
    }

    private fun normalizeNonNegative(amount: BigDecimal): BigDecimal? {
        if (amount < BigDecimal.ZERO) {
            return null
        }
        return amount.setScale(currency.scale, RoundingMode.HALF_UP)
    }

    override fun close() {
        // データベースのライフサイクルは ShimeDatabaseManager が管理します。
    }
}
