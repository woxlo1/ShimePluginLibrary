package shimeserver.shimepluginlibrary.shimeeconomy

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimedatabase.ShimeDatabaseManager
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * データベースが利用可能な場合は永続化された経済システムを選択し、そうでない場合はインメモリ実装に
 * フォールバックして、その選択を明示的にログに出力します。
 */
class ShimeEconomyManager(
    private val databaseManager: ShimeDatabaseManager,
    private val logger: ShimeLogger,
    private val currency: ShimeCurrency = ShimeCurrency()
) : ShimeAbstractService(SERVICE_NAME, 0, setOf(ShimeDatabaseManager.SERVICE_NAME)) {
    @Volatile
    private var provider: ShimeEconomyService = ShimeInMemoryEconomyService(currency)

    @Volatile
    private var persistent: Boolean = false

    override fun onInitialize(context: ShimeContext) {
        val database = databaseManager.default()
        if (database == null) {
            logger.warn(
                ShimeLogCategory.ECONOMY,
                "No database is connected; the economy is using in-memory storage and will not persist across restarts."
            )
            provider = ShimeInMemoryEconomyService(currency)
            persistent = false
            return
        }
        val databaseEconomy = ShimeDatabaseEconomyService(database, currency, logger)
        databaseEconomy.initialize()
        provider = databaseEconomy
        persistent = true
        logger.info(ShimeLogCategory.ECONOMY, "Economy is backed by database '${database.name}'.")
    }

    override fun onShutdown() {
        provider.close()
    }

    fun provider(): ShimeEconomyService = provider

    fun setProvider(provider: ShimeEconomyService, persistent: Boolean = false) {
        this.provider.close()
        this.provider = provider
        this.persistent = persistent
    }

    fun isPersistent(): Boolean = persistent

    fun currency(): ShimeCurrency = provider.currency

    fun balance(account: UUID): BigDecimal = provider.balance(account)

    fun setBalance(account: UUID, amount: BigDecimal, reason: String? = null): ShimeTransactionResult {
        return provider.setBalance(account, amount, reason)
    }

    fun deposit(account: UUID, amount: BigDecimal, reason: String? = null): ShimeTransactionResult {
        return provider.deposit(account, amount, reason)
    }

    fun withdraw(account: UUID, amount: BigDecimal, reason: String? = null): ShimeTransactionResult {
        return provider.withdraw(account, amount, reason)
    }

    fun transfer(from: UUID, to: UUID, amount: BigDecimal, reason: String? = null): ShimeTransactionResult {
        return provider.transfer(from, to, amount, reason)
    }

    fun history(account: UUID, limit: Int = 50): List<ShimeTransactionRecord> = provider.history(account, limit)

    fun balanceAsync(account: UUID): CompletableFuture<BigDecimal> = provider.balanceAsync(account)

    fun depositAsync(account: UUID, amount: BigDecimal, reason: String? = null): CompletableFuture<ShimeTransactionResult> {
        return provider.depositAsync(account, amount, reason)
    }

    fun withdrawAsync(account: UUID, amount: BigDecimal, reason: String? = null): CompletableFuture<ShimeTransactionResult> {
        return provider.withdrawAsync(account, amount, reason)
    }

    fun transferAsync(
        from: UUID,
        to: UUID,
        amount: BigDecimal,
        reason: String? = null
    ): CompletableFuture<ShimeTransactionResult> {
        return provider.transferAsync(from, to, amount, reason)
    }

    companion object {
        const val SERVICE_NAME = "economy"
    }
}
