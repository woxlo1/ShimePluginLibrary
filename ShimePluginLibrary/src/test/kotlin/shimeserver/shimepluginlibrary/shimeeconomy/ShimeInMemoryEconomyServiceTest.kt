package shimeserver.shimepluginlibrary.shimeeconomy

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShimeInMemoryEconomyServiceTest {
    private fun amount(value: String): BigDecimal = BigDecimal(value)

    @Test
    fun `new account starts at zero`() {
        val economy = ShimeInMemoryEconomyService()
        assertEquals(BigDecimal("0.00"), economy.balance(UUID.randomUUID()))
    }

    @Test
    fun `deposit and withdraw update the balance`() {
        val economy = ShimeInMemoryEconomyService()
        val account = UUID.randomUUID()

        val deposit = economy.deposit(account, amount("10.005"))
        assertTrue(deposit.success)
        assertEquals(BigDecimal("10.01"), deposit.balance)
        assertEquals(BigDecimal("10.01"), economy.balance(account))

        val withdraw = economy.withdraw(account, amount("2.50"))
        assertTrue(withdraw.success)
        assertEquals(BigDecimal("7.51"), economy.balance(account))
    }

    @Test
    fun `withdraw rejects insufficient funds without changing the balance`() {
        val economy = ShimeInMemoryEconomyService()
        val account = UUID.randomUUID()
        economy.deposit(account, amount("3.00"))

        val result = economy.withdraw(account, amount("3.01"))

        assertFalse(result.success)
        assertEquals("Insufficient balance.", result.reason)
        assertEquals(BigDecimal("3.00"), economy.balance(account))
    }

    @Test
    fun `rejects non-positive amounts`() {
        val economy = ShimeInMemoryEconomyService()
        val account = UUID.randomUUID()

        assertFalse(economy.deposit(account, BigDecimal.ZERO).success)
        assertFalse(economy.deposit(account, amount("-1.00")).success)
        assertFalse(economy.setBalance(account, amount("-1.00")).success)
    }

    @Test
    fun `setBalance normalizes to the currency scale`() {
        val economy = ShimeInMemoryEconomyService(ShimeCurrency(scale = 0))
        val account = UUID.randomUUID()

        val result = economy.setBalance(account, amount("4.6"))

        assertTrue(result.success)
        assertEquals(BigDecimal("5"), economy.balance(account))
    }

    @Test
    fun `transfer moves funds atomically between accounts`() {
        val economy = ShimeInMemoryEconomyService()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        economy.deposit(from, amount("20.00"))

        val result = economy.transfer(from, to, amount("5.25"), "test")

        assertTrue(result.success)
        assertEquals(BigDecimal("14.75"), economy.balance(from))
        assertEquals(BigDecimal("5.25"), economy.balance(to))
        assertEquals(ShimeTransactionType.TRANSFER_OUT, economy.history(from, 1).single().type)
        assertEquals(ShimeTransactionType.TRANSFER_IN, economy.history(to, 1).single().type)
        assertEquals(result.transactionId, economy.history(to, 1).single().transactionId)
    }

    @Test
    fun `transfer rejects the same account`() {
        val economy = ShimeInMemoryEconomyService()
        val account = UUID.randomUUID()
        economy.deposit(account, amount("1.00"))

        val result = economy.transfer(account, account, amount("1.00"))

        assertFalse(result.success)
        assertEquals(BigDecimal("1.00"), economy.balance(account))
    }

    @Test
    fun `history is newest first and respects the limit`() {
        val economy = ShimeInMemoryEconomyService()
        val account = UUID.randomUUID()
        economy.deposit(account, amount("1.00"))
        economy.deposit(account, amount("2.00"))
        economy.withdraw(account, amount("0.50"))

        val history = economy.history(account, 2)

        assertEquals(2, history.size)
        assertEquals(ShimeTransactionType.WITHDRAW, history[0].type)
        assertEquals(ShimeTransactionType.DEPOSIT, history[1].type)
        assertEquals(BigDecimal("2.50"), history[0].balanceAfter)
    }

    @Test
    fun `history rejects a non-positive limit`() {
        val economy = ShimeInMemoryEconomyService()
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            economy.history(UUID.randomUUID(), 0)
        }
    }

    @Test
    fun `close clears all state`() {
        val economy = ShimeInMemoryEconomyService()
        val account = UUID.randomUUID()
        economy.deposit(account, amount("5.00"))

        economy.close()

        assertEquals(BigDecimal("0.00"), economy.balance(account))
        assertTrue(economy.accounts().isEmpty())
        assertTrue(economy.history(account, 10).isEmpty())
    }
}
