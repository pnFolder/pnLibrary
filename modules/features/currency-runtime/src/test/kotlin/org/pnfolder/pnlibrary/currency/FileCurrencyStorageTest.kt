package org.pnfolder.pnlibrary.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Path
import java.util.UUID

class FileCurrencyStorageTest {
    @Test
    fun `close persists queued transactions before returning`(@TempDir directory: Path) {
        val file = directory.resolve("queued.json")
        val currency = CurrencyKey(PluginId.of("test"), "coins")
        val account = CurrencyAccount(UUID.randomUUID())
        val descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)
        val storage = FileCurrencyStorage(file, 100)
        val operations = (1..20).map {
            storage.transact(currency, descriptor, CurrencyTransactionRequest(
                type = CurrencyTransactionType.CREDIT, amount = BigDecimal.ONE, target = account,
                actor = CurrencyActor.system("test"), service = "close-test",
            )).toCompletableFuture()
        }
        storage.close()
        operations.forEach { assertEquals(CurrencyTransactionStatus.COMMITTED, it.join().status) }
        FileCurrencyStorage(file, 100).use {
            assertEquals(BigDecimal("20.00"), it.balance(currency, account).toCompletableFuture().join())
        }
    }

    @Test
    fun `transaction survives close and reopen`(@TempDir directory: Path) {
        val file = directory.resolve("currency.json")
        val currency = CurrencyKey(PluginId.of("test"), "coins")
        val account = CurrencyAccount(UUID.randomUUID())
        val descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)
        val request = CurrencyTransactionRequest(
            type = CurrencyTransactionType.CREDIT,
            amount = BigDecimal("12.50"),
            target = account,
            actor = CurrencyActor.system("test"),
            service = "test-suite",
            reason = "round-trip",
            metadata = mapOf("source" to "unit-test"),
            idempotencyKey = "credit-1",
        )

        FileCurrencyStorage(file, 100).use { storage ->
            val transaction = storage.transact(currency, descriptor, request).toCompletableFuture().join()
            assertEquals(CurrencyTransactionStatus.COMMITTED, transaction.status)
        }

        FileCurrencyStorage(file, 100).use { storage ->
            assertEquals(BigDecimal("12.50"), storage.balance(currency, account).toCompletableFuture().join())
            val page = storage.history(currency, CurrencyHistoryQuery()).toCompletableFuture().join()
            assertEquals(1, page.items.size)
            assertEquals("round-trip", page.items.single().reason)
            assertEquals(mapOf("source" to "unit-test"), page.items.single().metadata)
            assertFalse(page.hasMore)
        }
    }
}
