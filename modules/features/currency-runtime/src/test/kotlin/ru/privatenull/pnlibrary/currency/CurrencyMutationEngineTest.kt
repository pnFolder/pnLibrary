package ru.privatenull.pnlibrary.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.currency.CurrencyAccount
import ru.privatenull.pnlibrary.api.currency.CurrencyActor
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionRequest
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionStatus
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionType
import java.math.BigDecimal
import java.util.UUID

class CurrencyMutationEngineTest {
    @Test
    fun `credit adds amount to target`() {
        val mutation = CurrencyMutationEngine.evaluate(
            request(CurrencyTransactionType.CREDIT, "2.50", target = account()),
            sourceBefore = null,
            targetBefore = decimal("10.00"),
        )

        assertEquals(CurrencyTransactionStatus.COMMITTED, mutation.status)
        assertEquals(decimal("12.50"), mutation.targetAfter)
    }

    @Test
    fun `insufficient debit is rejected without changing source`() {
        val mutation = CurrencyMutationEngine.evaluate(
            request(CurrencyTransactionType.DEBIT, "10.01", source = account()),
            sourceBefore = decimal("10.00"),
            targetBefore = null,
        )

        assertEquals(CurrencyTransactionStatus.REJECTED, mutation.status)
        assertEquals(decimal("10.00"), mutation.sourceAfter)
        assertEquals("Insufficient funds", mutation.failure)
    }

    @Test
    fun `transfer updates both balances as one transition`() {
        val mutation = CurrencyMutationEngine.evaluate(
            request(CurrencyTransactionType.TRANSFER, "3.25", source = account(), target = account()),
            sourceBefore = decimal("8.00"),
            targetBefore = decimal("1.50"),
        )

        assertEquals(CurrencyTransactionStatus.COMMITTED, mutation.status)
        assertEquals(decimal("4.75"), mutation.sourceAfter)
        assertEquals(decimal("4.75"), mutation.targetAfter)
    }

    private fun request(
        type: CurrencyTransactionType,
        amount: String,
        source: CurrencyAccount? = null,
        target: CurrencyAccount? = null,
    ) = CurrencyTransactionRequest(
        type = type,
        amount = decimal(amount),
        source = source,
        target = target,
        actor = CurrencyActor.system("test"),
        service = "test",
    )

    private fun account() = CurrencyAccount(UUID.randomUUID())
    private fun decimal(value: String) = BigDecimal(value)
}
