package org.pnfolder.pnlibrary.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryPage
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode
import org.pnfolder.pnlibrary.api.currency.CurrencyImportResult
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

class ManagedCurrencyProviderTest {
    @Test
    fun `invalid transfer is rejected before storage invocation`() {
        val storage = RecordingStorage()
        val provider = provider(storage)
        val account = CurrencyAccount(UUID.randomUUID())
        val request = request(
            type = CurrencyTransactionType.TRANSFER,
            amount = "1.00",
            source = account,
            target = account,
        )

        val result = provider.transact(request).toCompletableFuture().join()

        assertEquals(CurrencyTransactionStatus.REJECTED, result.status)
        assertEquals("Transfer source and target must differ", result.failure)
        assertFalse(storage.transacted)
    }

    @Test
    fun `amount with excess precision is rejected before storage invocation`() {
        val storage = RecordingStorage()
        val result = provider(storage).transact(
            request(CurrencyTransactionType.CREDIT, "1.001", target = CurrencyAccount(UUID.randomUUID())),
        ).toCompletableFuture().join()

        assertEquals(CurrencyTransactionStatus.REJECTED, result.status)
        assertFalse(storage.transacted)
    }

    @Test
    fun `owned storage is closed with provider`() {
        val storage = RecordingStorage()
        provider(storage, ownsStorage = true).close()
        assertTrue(storage.closed)
    }

    private fun provider(storage: CurrencyStorage, ownsStorage: Boolean = false) = ManagedCurrencyProvider(
        key = CurrencyKey(PluginId.of("test"), "coins"),
        descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN),
        storage = storage,
        service = "test",
        ownsStorage = ownsStorage,
        commandSettings = CurrencyCommandSettings(),
    )

    private fun request(
        type: CurrencyTransactionType,
        amount: String,
        source: CurrencyAccount? = null,
        target: CurrencyAccount? = null,
    ) = CurrencyTransactionRequest(
        type = type,
        amount = BigDecimal(amount),
        source = source,
        target = target,
        actor = CurrencyActor.system("test"),
        service = "test",
    )

    private class RecordingStorage : CurrencyStorage {
        var transacted = false
        var closed = false

        override fun balance(currency: CurrencyKey, account: CurrencyAccount) =
            CompletableFuture.completedFuture(BigDecimal.ZERO)

        override fun transact(
            currency: CurrencyKey,
            descriptor: CurrencyDescriptor,
            request: CurrencyTransactionRequest,
        ): CompletableFuture<CurrencyTransaction> {
            transacted = true
            return CompletableFuture.completedFuture(
                CurrencyTransaction(
                    id = UUID.randomUUID(),
                    currency = currency,
                    type = request.type,
                    status = CurrencyTransactionStatus.COMMITTED,
                    amount = request.amount,
                    source = request.source,
                    target = request.target,
                    actor = request.actor,
                    service = request.service,
                    reason = request.reason,
                    metadata = request.metadata,
                    createdAt = Instant.now(),
                ),
            )
        }

        override fun history(currency: CurrencyKey, query: CurrencyHistoryQuery) =
            CompletableFuture.completedFuture(CurrencyHistoryPage(emptyList(), query.offset, false))

        override fun export(currency: CurrencyKey) = CompletableFuture.completedFuture(
            CurrencyStorageSnapshot(currency = currency, createdAt = Instant.now(), balances = emptyMap(), transactions = emptyList()),
        )

        override fun importSnapshot(snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode) =
            CompletableFuture.completedFuture(CurrencyImportResult(snapshot.currency, mode, 0, 0, 0, 0))

        override fun close() {
            closed = true
        }
    }
}
