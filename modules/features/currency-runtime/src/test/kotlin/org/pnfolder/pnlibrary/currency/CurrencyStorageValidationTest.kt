package org.pnfolder.pnlibrary.currency

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Path
import java.util.UUID

class CurrencyStorageValidationTest {
    @TempDir lateinit var directory: Path
    private val currency = CurrencyKey(PluginId.of("test"), "coins")
    private val descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)
    private val account = CurrencyAccount(UUID.randomUUID())

    @Test
    fun `direct storage requests cannot bypass validation or change balances`() {
        for (backend in listOf("file", "jdbc")) storage(backend).use { storage ->
            storage.transact(currency, descriptor, request(CurrencyTransactionType.CREDIT, "10", target = account))
                .toCompletableFuture().join()
            val invalid = listOf(
                request(CurrencyTransactionType.CREDIT, "-1", target = account),
                request(CurrencyTransactionType.DEBIT, "-1", source = account),
                request(CurrencyTransactionType.CREDIT, "0", target = account),
                request(CurrencyTransactionType.CREDIT, "1.001", target = account),
                request(CurrencyTransactionType.TRANSFER, "1", source = account, target = account),
                request(CurrencyTransactionType.TRANSFER, "1", source = account),
                request(CurrencyTransactionType.CREDIT, "1"),
                request(CurrencyTransactionType.DEBIT, "1"),
                request(CurrencyTransactionType.SET_BALANCE, "-1", target = account),
                request(CurrencyTransactionType.RESET, "1", target = account),
            )
            invalid.forEachIndexed { index, request ->
                val retryable = request.copy(idempotencyKey = "invalid-$index")
                val transaction = storage.transact(currency, descriptor, retryable).toCompletableFuture().join()
                assertEquals(CurrencyTransactionStatus.REJECTED, transaction.status, "$backend: $request")
                assertFalse(transaction.failure.isNullOrBlank())
                assertEquals(transaction.id, storage.transact(currency, descriptor, retryable).toCompletableFuture().join().id)
                assertEquals(0, BigDecimal.TEN.compareTo(storage.balance(currency, account).toCompletableFuture().join()), backend)
            }
            assertEquals(invalid.size + 1, storage.history(currency, CurrencyHistoryQuery()).toCompletableFuture().join().items.size)
        }
    }

    @Test
    fun `unnecessary rounding rejects excess precision without throwing`() {
        val strict = descriptor.copy(roundingMode = RoundingMode.UNNECESSARY)
        assertFalse(strict.accepts(BigDecimal("1.001")))
        for (backend in listOf("file", "jdbc")) storage(backend).use { storage ->
            val transaction = storage.transact(currency, strict, request(CurrencyTransactionType.CREDIT, "1.001", target = account))
                .toCompletableFuture().join()
            assertEquals(CurrencyTransactionStatus.REJECTED, transaction.status, backend)
            assertEquals(0, storage.balance(currency, account).toCompletableFuture().join().signum(), backend)
        }
    }

    private fun storage(backend: String): CurrencyStorage = if (backend == "file") {
        FileCurrencyStorage(directory.resolve("${UUID.randomUUID()}.json"), 100)
    } else {
        JdbcCurrencyStorage(JdbcDataSource().apply {
            setURL("jdbc:h2:mem:validation-${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1")
        }, "pn_currency")
    }

    private fun request(
        type: CurrencyTransactionType,
        amount: String,
        source: CurrencyAccount? = null,
        target: CurrencyAccount? = null,
    ) = CurrencyTransactionRequest(type, BigDecimal(amount), source, target, CurrencyActor.system("test"), "validation-test")
}
