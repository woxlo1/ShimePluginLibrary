package shimeserver.shimepluginlibrary.shimeeconomy

import java.math.BigDecimal
import java.util.UUID

/** 経済操作の結果。失敗は例外ではなく想定された結果です。 */
data class ShimeTransactionResult(
    val success: Boolean,
    val transactionId: String?,
    val balance: BigDecimal?,
    val reason: String?,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        @JvmStatic
        fun success(transactionId: String, balance: BigDecimal): ShimeTransactionResult {
            return ShimeTransactionResult(true, transactionId, balance, null)
        }

        @JvmStatic
        fun failure(reason: String): ShimeTransactionResult {
            return ShimeTransactionResult(false, null, null, reason)
        }
    }
}

/** 経済操作の片側を表す監査レコード。 */
data class ShimeTransactionRecord(
    val transactionId: String,
    val account: UUID,
    val type: ShimeTransactionType,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal,
    val reason: String?,
    val timestamp: Long
)

enum class ShimeTransactionType {
    DEPOSIT,
    WITHDRAW,
    TRANSFER_IN,
    TRANSFER_OUT,
    SET
}
