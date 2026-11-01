package shimeserver.shimepluginlibrary.shimeeconomy

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * データベースが設定されていない場合に使用される、スレッドセーフなインメモリ経済システム。
 *
 * 意図的に永続化は行いません。本番サーバーではデータベースを基盤とする経済システムを接続してください。
 */
class ShimeInMemoryEconomyService(
    override val currency: ShimeCurrency = ShimeCurrency()
) : ShimeEconomyService {
    private val lock = Any()
    private val balances = ConcurrentHashMap<UUID, BigDecimal>()
    private val records = ConcurrentHashMap<UUID, MutableList<ShimeTransactionRecord>>()

    override fun balance(account: UUID): BigDecimal = synchronized(lock) {
        balances[account]?.setScale(currency.scale, RoundingMode.HALF_UP) ?: BigDecimal.ZERO.setScale(currency.scale)
    }

    override fun setBalance(account: UUID, amount: BigDecimal, reason: String?): ShimeTransactionResult {
        val normalized = normalize(amount) ?: return ShimeTransactionResult.failure("Amount must not be negative.")
        synchronized(lock) {
            balances[account] = normalized
            val transactionId = UUID.randomUUID().toString()
            appendRecord(account, ShimeTransactionType.SET, normalized, normalized, reason, transactionId)
            return ShimeTransactionResult.success(transactionId, normalized)
        }
    }

    override fun deposit(account: UUID, amount: BigDecimal, reason: String?): ShimeTransactionResult {
        val normalized = normalize(amount) ?: return ShimeTransactionResult.failure("Amount must be positive.")
        synchronized(lock) {
            val current = balances[account] ?: BigDecimal.ZERO.setScale(currency.scale)
            val updated = current.add(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
            balances[account] = updated
            val transactionId = UUID.randomUUID().toString()
            appendRecord(account, ShimeTransactionType.DEPOSIT, normalized, updated, reason, transactionId)
            return ShimeTransactionResult.success(transactionId, updated)
        }
    }

    override fun withdraw(account: UUID, amount: BigDecimal, reason: String?): ShimeTransactionResult {
        val normalized = normalize(amount) ?: return ShimeTransactionResult.failure("Amount must be positive.")
        synchronized(lock) {
            val current = balances[account] ?: BigDecimal.ZERO.setScale(currency.scale)
            if (current < normalized) {
                return ShimeTransactionResult.failure("Insufficient balance.")
            }
            val updated = current.subtract(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
            balances[account] = updated
            val transactionId = UUID.randomUUID().toString()
            appendRecord(account, ShimeTransactionType.WITHDRAW, normalized, updated, reason, transactionId)
            return ShimeTransactionResult.success(transactionId, updated)
        }
    }

    override fun transfer(from: UUID, to: UUID, amount: BigDecimal, reason: String?): ShimeTransactionResult {
        if (from == to) {
            return ShimeTransactionResult.failure("Sender and receiver must be different accounts.")
        }
        val normalized = normalize(amount) ?: return ShimeTransactionResult.failure("Amount must be positive.")
        synchronized(lock) {
            val fromBalance = balances[from] ?: BigDecimal.ZERO.setScale(currency.scale)
            if (fromBalance < normalized) {
                return ShimeTransactionResult.failure("Insufficient balance.")
            }
            val toBalance = balances[to] ?: BigDecimal.ZERO.setScale(currency.scale)
            val fromUpdated = fromBalance.subtract(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
            val toUpdated = toBalance.add(normalized).setScale(currency.scale, RoundingMode.HALF_UP)
            balances[from] = fromUpdated
            balances[to] = toUpdated
            val transactionId = UUID.randomUUID().toString()
            appendRecord(from, ShimeTransactionType.TRANSFER_OUT, normalized, fromUpdated, reason, transactionId)
            appendRecord(to, ShimeTransactionType.TRANSFER_IN, normalized, toUpdated, reason, transactionId)
            return ShimeTransactionResult.success(transactionId, fromUpdated)
        }
    }

    override fun history(account: UUID, limit: Int): List<ShimeTransactionRecord> {
        require(limit > 0) { "History limit must be positive." }
        synchronized(lock) {
            val list = records[account] ?: return emptyList()
            return list.takeLast(limit).asReversed()
        }
    }

    fun accounts(): Set<UUID> = balances.keys.toSet()

    override fun close() {
        synchronized(lock) {
            balances.clear()
            records.clear()
        }
    }

    private fun normalize(amount: BigDecimal): BigDecimal? {
        if (amount <= BigDecimal.ZERO) {
            return null
        }
        if (amount.scale() > currency.scale) {
            val rounded = amount.setScale(currency.scale, RoundingMode.HALF_UP)
            return if (rounded <= BigDecimal.ZERO) null else rounded
        }
        return amount.setScale(currency.scale, RoundingMode.HALF_UP)
    }

    private fun appendRecord(
        account: UUID,
        type: ShimeTransactionType,
        amount: BigDecimal,
        balanceAfter: BigDecimal,
        reason: String?,
        transactionId: String
    ) {
        val list = records.computeIfAbsent(account) { Collections.synchronizedList(ArrayList()) }
        list.add(
            ShimeTransactionRecord(
                transactionId = transactionId,
                account = account,
                type = type,
                amount = amount,
                balanceAfter = balanceAfter,
                reason = reason,
                timestamp = System.currentTimeMillis()
            )
        )
        while (list.size > 2_000) {
            list.removeAt(0)
        }
    }
}
