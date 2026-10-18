package ru.privatenull.pnlibrary.currency

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.currency.CurrencyAccount
import ru.privatenull.pnlibrary.api.currency.CurrencyActor
import ru.privatenull.pnlibrary.api.currency.CurrencyDescriptor
import ru.privatenull.pnlibrary.api.currency.CurrencyHistoryQuery
import ru.privatenull.pnlibrary.api.currency.CurrencyImportMode
import ru.privatenull.pnlibrary.api.currency.CurrencyKey
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionRequest
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionStatus
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionType
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/** Runs the JDBC storage against an in-memory H2 database in MySQL mode, the dialect servers use most. */
class JdbcCurrencyStorageTest {
    private val source = JdbcDataSource().apply {
        setURL("jdbc:h2:mem:currency-${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1")
    }
    private val storage = JdbcCurrencyStorage(source, "pn_currency")
    private val currency = CurrencyKey(PluginId.of("test"), "coins")
    private val descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)
    private val account = CurrencyAccount(UUID.randomUUID())

    @AfterEach
    fun close() = storage.close()

    @Test
    fun `credits and debits update the balance and the ledger`() {
        transact(CurrencyTransactionType.CREDIT, "12.50", key = "credit-1")
        transact(CurrencyTransactionType.DEBIT, "2.25", key = "debit-1")

        assertEquals(0, BigDecimal("10.25").compareTo(storage.balance(currency, account).join()))
        val page = storage.history(currency, CurrencyHistoryQuery()).join()
        assertEquals(2, page.items.size)
        assertEquals(setOf("credit-1", "debit-1"), page.items.map { it.idempotencyKey }.toSet())
        assertFalse(page.hasMore)
    }

    @Test
    fun `a repeated idempotency key returns the first transaction and changes nothing`() {
        val first = transact(CurrencyTransactionType.CREDIT, "5", key = "once")
        val repeated = transact(CurrencyTransactionType.CREDIT, "5", key = "once")

        assertEquals(first.id, repeated.id)
        assertEquals(0, BigDecimal("5").compareTo(storage.balance(currency, account).join()))
        assertEquals(1, storage.history(currency, CurrencyHistoryQuery()).join().items.size)
    }

    @Test
    fun `an export restores into an empty store with the same balances and ledger`() {
        transact(CurrencyTransactionType.CREDIT, "7.5", key = "credit-1")
        val snapshot = storage.export(currency).join()

        val target = JdbcCurrencyStorage(JdbcDataSource().apply {
            setURL("jdbc:h2:mem:currency-target-${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1")
        }, "pn_currency")
        target.use {
            val result = it.importSnapshot(snapshot, CurrencyImportMode.REPLACE).join()
            assertEquals(1, result.importedAccounts)
            assertEquals(1, result.importedTransactions)
            assertEquals(0, BigDecimal("7.5").compareTo(it.balance(currency, account).join()))
            assertTrue(it.history(currency, CurrencyHistoryQuery()).join().items.single().status == CurrencyTransactionStatus.COMMITTED)
        }
    }

    private fun transact(type: CurrencyTransactionType, amount: String, key: String) = storage.transact(
        currency,
        descriptor,
        CurrencyTransactionRequest(
            type = type,
            amount = BigDecimal(amount),
            source = account.takeIf { type == CurrencyTransactionType.DEBIT },
            target = account.takeIf { type == CurrencyTransactionType.CREDIT },
            actor = CurrencyActor.system("test"),
            service = "test-suite",
            reason = "unit-test",
            idempotencyKey = key,
        ),
    ).join()

    private fun <T> java.util.concurrent.CompletionStage<T>.join(): T = toCompletableFuture().join()
}
