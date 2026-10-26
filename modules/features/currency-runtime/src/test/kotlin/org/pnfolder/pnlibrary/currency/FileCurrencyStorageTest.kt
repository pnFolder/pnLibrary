package org.pnfolder.pnlibrary.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletionException

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

    private val currency = CurrencyKey(PluginId.of("test"), "coins")
    private val descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)

    private fun FileCurrencyStorage.credit(account: CurrencyAccount, amount: String, key: String? = null) = transact(
        currency, descriptor,
        CurrencyTransactionRequest(
            type = CurrencyTransactionType.CREDIT, amount = BigDecimal(amount), target = account,
            actor = CurrencyActor.system("test"), service = "file-test", idempotencyKey = key,
        ),
    ).toCompletableFuture().join()

    private fun FileCurrencyStorage.balanceOf(account: CurrencyAccount) = balance(currency, account).toCompletableFuture().join()

    private fun FileCurrencyStorage.history(query: CurrencyHistoryQuery = CurrencyHistoryQuery()) =
        history(currency, query).toCompletableFuture().join()

    @Test
    fun `a failed write leaves balances, history and idempotency untouched`(@TempDir directory: Path) {
        val folder = directory.resolve("data")
        val account = CurrencyAccount(UUID.randomUUID())
        FileCurrencyStorage(folder.resolve("currency.json"), 100).use { storage ->
            storage.credit(account, "5", key = "first")
            // The data folder becomes a regular file, so the next save cannot succeed.
            Files.walk(folder).sorted(Comparator.reverseOrder()).forEach(Files::delete)
            Files.write(folder, byteArrayOf(0))

            assertThrows(CompletionException::class.java) { storage.credit(account, "7", key = "second") }

            assertEquals(0, BigDecimal("5").compareTo(storage.balanceOf(account)))
            assertEquals(1, storage.history().items.size)
            Files.delete(folder)
            // The failed key was forgotten, so it may be retried once storage works again.
            assertEquals(CurrencyTransactionStatus.COMMITTED, storage.credit(account, "7", key = "second").status)
            assertEquals(0, BigDecimal("12").compareTo(storage.balanceOf(account)))
        }
    }

    @Test
    fun `the oldest transactions are evicted and their idempotency keys released`(@TempDir directory: Path) {
        val account = CurrencyAccount(UUID.randomUUID())
        FileCurrencyStorage(directory.resolve("currency.json"), 100).use { storage ->
            storage.credit(account, "1", key = "oldest")
            repeat(100) { storage.credit(account, "1") }

            assertEquals(100, storage.history(CurrencyHistoryQuery(limit = 500)).items.size)
            // "oldest" was evicted, so the same key now creates a new transaction instead of replaying.
            val again = storage.credit(account, "1", key = "oldest")
            assertEquals(0, BigDecimal("102").compareTo(storage.balanceOf(account)))
            assertEquals("oldest", again.idempotencyKey)
        }
    }

    @Test
    fun `merge keeps existing accounts while replace discards them`(@TempDir directory: Path) {
        val kept = CurrencyAccount(UUID.randomUUID())
        val added = CurrencyAccount(UUID.randomUUID())
        val snapshot = FileCurrencyStorage(directory.resolve("source.json"), 100).use { source ->
            source.credit(kept, "50")
            source.credit(added, "3")
            source.export(currency).toCompletableFuture().join()
        }

        FileCurrencyStorage(directory.resolve("merge.json"), 100).use { target ->
            target.credit(kept, "1")
            val result = target.importSnapshot(snapshot, CurrencyImportMode.MERGE_KEEP_TARGET).toCompletableFuture().join()

            assertEquals(1, result.importedAccounts)
            assertEquals(1, result.skippedAccounts)
            assertEquals(0, BigDecimal("1").compareTo(target.balanceOf(kept)))
            assertEquals(0, BigDecimal("3").compareTo(target.balanceOf(added)))
        }
        FileCurrencyStorage(directory.resolve("replace.json"), 100).use { target ->
            target.credit(kept, "1")
            target.importSnapshot(snapshot, CurrencyImportMode.REPLACE).toCompletableFuture().join()

            assertEquals(0, BigDecimal("50").compareTo(target.balanceOf(kept)))
            assertEquals(snapshot.transactions.size, target.history().items.size)
        }
    }

    @Test
    fun `history is filtered by account and paged newest first`(@TempDir directory: Path) {
        val first = CurrencyAccount(UUID.randomUUID())
        val second = CurrencyAccount(UUID.randomUUID())
        FileCurrencyStorage(directory.resolve("currency.json"), 100).use { storage ->
            repeat(3) { storage.credit(first, "1", key = "a$it") }
            storage.credit(second, "1", key = "b0")

            val own = storage.history(CurrencyHistoryQuery(account = first.playerId))
            assertEquals(listOf("a2", "a1", "a0"), own.items.map { it.idempotencyKey })

            val page = storage.history(CurrencyHistoryQuery(limit = 2, offset = 1))
            assertEquals(listOf("a2", "a1"), page.items.map { it.idempotencyKey })
            assertTrue(page.hasMore)
        }
    }

    @Test
    fun `a corrupt storage file stops the feature instead of starting with empty balances`(@TempDir directory: Path) {
        val file = Files.writeString(directory.resolve("currency.json"), "{ not json")

        assertThrows(RuntimeException::class.java) { FileCurrencyStorage(file, 100) }
    }
}
