package org.pnfolder.pnlibrary.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Random
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class FileCurrencyStorageBatchTest {
    private val currency = CurrencyKey(PluginId.of("test"), "coins")
    private val descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)
    private val accounts = (1..5).map { CurrencyAccount(UUID(0, it.toLong())) }

    private fun corpus(count: Int): List<CurrencyTransactionRequest> {
        val random = Random(42)
        return (0 until count).map { index ->
            val type = CurrencyTransactionType.entries[random.nextInt(CurrencyTransactionType.entries.size)]
            val amount = when {
                type == CurrencyTransactionType.RESET -> BigDecimal.ZERO
                index % 13 == 0 -> BigDecimal("1.005")
                else -> BigDecimal(random.nextInt(900) + 1).movePointLeft(2)
            }
            val first = accounts[random.nextInt(accounts.size)]
            val second = accounts[random.nextInt(accounts.size)]
            CurrencyTransactionRequest(
                type = type, amount = amount,
                source = if (type == CurrencyTransactionType.DEBIT || type == CurrencyTransactionType.TRANSFER) first else null,
                target = if (type == CurrencyTransactionType.TRANSFER) second else if (type == CurrencyTransactionType.DEBIT) null else first,
                actor = CurrencyActor.system("test"), service = "batch-test",
                idempotencyKey = if (index % 7 == 0) "key-${random.nextInt(20)}" else null,
            )
        }
    }

    private fun describe(transaction: CurrencyTransaction) = listOf(
        transaction.type, transaction.status, transaction.amount, transaction.source?.playerId, transaction.target?.playerId,
        transaction.sourceBalanceBefore, transaction.sourceBalanceAfter, transaction.targetBalanceBefore, transaction.targetBalanceAfter,
        transaction.failure, transaction.idempotencyKey,
    ).joinToString("|")

    private fun state(storage: FileCurrencyStorage): String {
        val snapshot = storage.export(currency).toCompletableFuture().join()
        val balances = snapshot.balances.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
        return balances + "\n" + snapshot.transactions.joinToString("\n", transform = ::describe)
    }

    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun outcome(future: CompletableFuture<CurrencyTransaction>) = try {
        describe(future.join())
    } catch (error: CompletionException) {
        "failed:" + error.cause?.message
    }

    private fun run(file: Path, queued: Boolean, writer: ((Path, (Path) -> Unit) -> Unit)? = null): String {
        val storage = if (writer == null) FileCurrencyStorage(file, 100) else FileCurrencyStorage(file, 100, replaceFile = writer)
        storage.use {
            val futures = ArrayList<CompletableFuture<CurrencyTransaction>>()
            val log = StringBuilder()
            corpus(400).forEach { request ->
                val future = it.transact(currency, descriptor, request).toCompletableFuture()
                if (queued) futures += future else log.append(outcome(future)).append('\n')
            }
            futures.forEach { log.append(outcome(it)).append('\n') }
            log.append(state(it))
            return log.toString()
        }
    }

    @Test
    fun `corpus gives the same ledger when sequential or queued`(@TempDir directory: Path) {
        val sequential = run(directory.resolve("a.json"), queued = false)
        val queued = run(directory.resolve("b.json"), queued = true)
        assertEquals(sequential, queued)
        assertEquals(GOLDEN, sha(sequential))
    }

    @Test
    fun `corpus with failing writes gives the pinned result`(@TempDir directory: Path) {
        val calls = AtomicInteger()
        val failing = setOf(3, 7, 8, 100)
        val result = run(directory.resolve("c.json"), queued = false) { target, fill ->
            if (calls.incrementAndGet() in failing) error("disk full")
            AtomicFiles.write(target, durable = true, fill)
        }
        assertEquals(GOLDEN_FAILING, sha(result))
    }

    @Test
    fun `reloading the file gives the same balances and history`(@TempDir directory: Path) {
        val file = directory.resolve("d.json")
        val before = FileCurrencyStorage(file, 100).use { storage ->
            corpus(400).map { storage.transact(currency, descriptor, it) }.forEach { it.toCompletableFuture().join() }
            state(storage)
        }
        assertEquals(before, FileCurrencyStorage(file, 100).use(::state))
    }

    @Test
    fun `a thousand parallel transactions are saved in a few batches`(@TempDir directory: Path) {
        val file = directory.resolve("e.json")
        val saves = AtomicInteger()
        val written = AtomicLong()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val storage = FileCurrencyStorage(file, 100_000) { target, fill ->
            if (saves.incrementAndGet() == 1) {
                entered.countDown()
                assertTrue(release.await(10, TimeUnit.SECONDS))
            }
            AtomicFiles.write(target, durable = false, fill)
            written.addAndGet(Files.size(target))
        }
        storage.use {
            val credit = credit("1")
            val futures = ArrayList<CompletableFuture<CurrencyTransaction>>()
            futures += it.transact(currency, descriptor, credit).toCompletableFuture()
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            repeat(999) { _ -> futures += it.transact(currency, descriptor, credit).toCompletableFuture() }
            release.countDown()
            futures.forEach { future -> future.join() }
            assertEquals(BigDecimal("1000.00"), it.balance(currency, accounts[0]).toCompletableFuture().join())
        }
        val finalSize = Files.size(file)
        assertTrue(saves.get() <= 10, "saves=${saves.get()}")
        assertTrue(written.get() <= 10 * finalSize, "written=${written.get()} final=$finalSize")
    }

    @Test
    fun `a failed batch save keeps balances and idempotency and later transactions still work`(@TempDir directory: Path) {
        val broken = AtomicBoolean(false)
        val blockNext = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val storage = FileCurrencyStorage(directory.resolve("f.json"), 100) { target, fill ->
            val failed = broken.get()
            if (blockNext.compareAndSet(true, false)) {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            if (failed) error("disk full")
            AtomicFiles.write(target, durable = false, fill)
        }
        storage.use {
            it.transact(currency, descriptor, credit("5", "ok")).toCompletableFuture().join()
            blockNext.set(true)
            val blocker = it.transact(currency, descriptor, credit("1")).toCompletableFuture()
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            broken.set(true)
            val doomed = (1..5).map { n -> it.transact(currency, descriptor, credit("10", "doomed-$n")).toCompletableFuture() }
            release.countDown()
            blocker.join()
            doomed.forEach { future -> assertThrows(CompletionException::class.java) { future.join() } }
            assertEquals(0, BigDecimal("6").compareTo(it.balance(currency, accounts[0]).toCompletableFuture().join()))
            assertEquals(2, it.history(currency, CurrencyHistoryQuery()).toCompletableFuture().join().items.size)
            broken.set(false)
            val retried = it.transact(currency, descriptor, credit("10", "doomed-1")).toCompletableFuture().join()
            assertEquals(0, BigDecimal("16").compareTo(retried.targetBalanceAfter))
        }
    }

    @Test
    fun `a failed batch brings back evicted history and its idempotency keys`(@TempDir directory: Path) {
        val broken = AtomicBoolean(false)
        val blockNext = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val storage = FileCurrencyStorage(directory.resolve("g.json"), 100) { target, fill ->
            val failed = broken.get()
            if (blockNext.compareAndSet(true, false)) {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            if (failed) error("disk full")
            AtomicFiles.write(target, durable = false, fill)
        }
        storage.use {
            repeat(99) { n -> it.transact(currency, descriptor, credit("1", "old-$n")).toCompletableFuture().join() }
            blockNext.set(true)
            val blocker = it.transact(currency, descriptor, credit("0.01")).toCompletableFuture()
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            broken.set(true)
            val doomed = (1..30).map { n -> it.transact(currency, descriptor, credit("2", "new-$n")).toCompletableFuture() }
            release.countDown()
            blocker.join()
            doomed.forEach { future -> assertThrows(CompletionException::class.java) { future.join() } }
            broken.set(false)
            val replayed = it.transact(currency, descriptor, credit("5", "old-0")).toCompletableFuture().join()
            assertEquals(0, BigDecimal("1").compareTo(replayed.amount))
            assertEquals(100, it.history(currency, CurrencyHistoryQuery(limit = 500)).toCompletableFuture().join().items.size)
            assertEquals(0, BigDecimal("99.01").compareTo(it.balance(currency, accounts[0]).toCompletableFuture().join()))
        }
    }

    private fun credit(amount: String, key: String? = null) = CurrencyTransactionRequest(
        type = CurrencyTransactionType.CREDIT, amount = BigDecimal(amount), target = accounts[0],
        actor = CurrencyActor.system("test"), service = "batch-test", idempotencyKey = key,
    )

    private companion object {
        const val GOLDEN = "b2d6d39db3fda0f3a53ba7c27164e601886a2b5ad36b8f117abdb25c69c59d54"
        const val GOLDEN_FAILING = "66c00db1263b8b105cfe6a9a3a73e6f4fd76d3c1f1f355d321a7831f94647e14"
    }
}
