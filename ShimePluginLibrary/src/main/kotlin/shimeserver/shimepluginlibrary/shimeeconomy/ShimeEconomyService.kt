package shimeserver.shimepluginlibrary.shimeeconomy

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 経済プロバイダーの契約。
 *
 * 実装は残高をアトミックに扱わなければなりません。呼び出し側は引き出しや送金が成功したと仮定せず、
 * [ShimeTransactionResult.success] を確認してください。
 */
interface ShimeEconomyService : AutoCloseable {
    val currency: ShimeCurrency

    fun balance(account: UUID): BigDecimal

    fun setBalance(account: UUID, amount: BigDecimal, reason: String? = null): ShimeTransactionResult

    fun deposit(account: UUID, amount: BigDecimal, reason: String? = null): ShimeTransactionResult

    fun withdraw(account: UUID, amount: BigDecimal, reason: String? = null): ShimeTransactionResult

    fun transfer(
        from: UUID,
        to: UUID,
        amount: BigDecimal,
        reason: String? = null
    ): ShimeTransactionResult

    fun history(account: UUID, limit: Int = 50): List<ShimeTransactionRecord>

    fun balanceAsync(account: UUID): CompletableFuture<BigDecimal> {
        return CompletableFuture.completedFuture(balance(account))
    }

    fun depositAsync(account: UUID, amount: BigDecimal, reason: String? = null): CompletableFuture<ShimeTransactionResult> {
        return CompletableFuture.completedFuture(deposit(account, amount, reason))
    }

    fun withdrawAsync(account: UUID, amount: BigDecimal, reason: String? = null): CompletableFuture<ShimeTransactionResult> {
        return CompletableFuture.completedFuture(withdraw(account, amount, reason))
    }

    fun transferAsync(
        from: UUID,
        to: UUID,
        amount: BigDecimal,
        reason: String? = null
    ): CompletableFuture<ShimeTransactionResult> {
        return CompletableFuture.completedFuture(transfer(from, to, amount, reason))
    }

    override fun close() {
        // ほとんどのプロバイダーはリソースを所有しません。
    }
}
