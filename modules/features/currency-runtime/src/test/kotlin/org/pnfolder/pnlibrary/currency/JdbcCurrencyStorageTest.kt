package org.pnfolder.pnlibrary.currency

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

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

    @Test
    fun `borrowed connections go back with their original transaction settings`() {
        val borrowed = mutableListOf<Connection>()
        val tracking = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DataSource::class.java)) { _, method, args ->
            val result = try {
                method.invoke(source, *(args ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
            if (method.name != "getConnection") return@newProxyInstance result
            val real = result as Connection
            // Keep the connection open after "close" so its state can be inspected, as a pool would.
            (Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, call, callArgs ->
                if (call.name == "close") null else call.invoke(real, *(callArgs ?: emptyArray()))
            } as Connection).also { borrowed += it }
        } as DataSource

        JdbcCurrencyStorage(tracking, "pn_currency_pooled").use {
            it.transact(
                currency,
                descriptor,
                CurrencyTransactionRequest(
                    type = CurrencyTransactionType.CREDIT,
                    amount = BigDecimal("1"),
                    target = account,
                    actor = CurrencyActor.system("test"),
                    service = "test-suite",
                    reason = "unit-test",
                    idempotencyKey = "pooled-1",
                ),
            ).join()
            it.export(currency).join()
        }

        assertTrue(borrowed.isNotEmpty())
        borrowed.forEach { connection ->
            assertTrue(connection.autoCommit)
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.transactionIsolation)
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
