package org.pnfolder.pnlibrary.currency

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class CurrencyFileBytesTest {
    private val currency = CurrencyKey(PluginId.of("test"), "coins")
    private val descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)
    private val accounts = (1..5).map { CurrencyAccount(UUID(0, it.toLong())) }
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val reasons = listOf(null, "plain", "quote ${34.toChar()} slash ${92.toChar()} tab${9.toChar()} line${10.toChar()}break",
        "<b>&'</b> é 日本 😀 ${8232.toChar()}", "")

    private fun requests(count: Int): List<CurrencyTransactionRequest> {
        val random = Random(7)
        return (0 until count).map { index ->
            val type = CurrencyTransactionType.entries[random.nextInt(CurrencyTransactionType.entries.size)]
            val first = accounts[random.nextInt(accounts.size)]
            val second = accounts[random.nextInt(accounts.size)]
            CurrencyTransactionRequest(
                type = type,
                amount = if (type == CurrencyTransactionType.RESET) BigDecimal.ZERO else BigDecimal(random.nextInt(900) + 1).movePointLeft(2),
                source = if (type == CurrencyTransactionType.DEBIT || type == CurrencyTransactionType.TRANSFER) first else null,
                target = if (type == CurrencyTransactionType.TRANSFER) second else if (type == CurrencyTransactionType.DEBIT) null else first,
                actor = CurrencyActor.system("test"), service = "bytes-test",
                reason = reasons[index % reasons.size],
                metadata = if (index % 3 == 0) mapOf("k$index" to "v \"$index\"", "é" to "<&>") else emptyMap(),
                idempotencyKey = if (index % 7 == 0) "key-${random.nextInt(20)}" else null,
            )
        }
    }

    /** What the storage wrote before the pieces were cached: the whole tree through Gson's pretty printer. */
    private fun legacyBytes(storage: FileCurrencyStorage): ByteArray {
        val snapshot = storage.export(currency).toCompletableFuture().join()
        val root = JsonObject()
        root.add("balances", JsonObject().also { values ->
            snapshot.balances.forEach { (id, value) ->
                values.addProperty(CurrencyLedgerState.accountKey(currency, CurrencyAccount(id)), value.toPlainString())
            }
        })
        root.add("transactions", JsonArray().also { values -> snapshot.transactions.forEach { values.add(CurrencyTransactionJson.encode(it)) } })
        return gson.toJson(root).toByteArray(Charsets.UTF_8)
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `the file is byte for byte what the whole-tree writer gives, with trimming and failed saves`(@TempDir directory: Path) {
        val file = directory.resolve("ledger.json")
        val calls = AtomicInteger()
        val failing = setOf(5, 6, 90, 91, 200)
        var compared = 0
        FileCurrencyStorage(file, 100, replaceFile = { target, fill ->
            if (calls.incrementAndGet() in failing) error("disk full")
            AtomicFiles.write(target, durable = false, fill)
        }).use { storage ->
            requests(400).forEachIndexed { index, request ->
                runCatching { storage.transact(currency, descriptor, request).toCompletableFuture().join() }
                if (index % 5 == 0 && Files.exists(file)) {
                    assertEquals(sha(legacyBytes(storage)), sha(Files.readAllBytes(file)), "after request $index")
                    compared++
                }
            }
            assertEquals(sha(legacyBytes(storage)), sha(Files.readAllBytes(file)))
        }
        assertTrue(compared > 70)
    }

    @Test
    fun `an emptied ledger and a reloaded file keep the same bytes`(@TempDir directory: Path) {
        val file = directory.resolve("ledger.json")
        FileCurrencyStorage(file, 1000).use { storage ->
            requests(60).forEach { storage.transact(currency, descriptor, it).toCompletableFuture().join() }
            val snapshot = storage.export(currency).toCompletableFuture().join()
            storage.importSnapshot(snapshot.copy(balances = emptyMap(), transactions = emptyList()), CurrencyImportMode.REPLACE)
                .toCompletableFuture().join()
            assertEquals(sha(legacyBytes(storage)), sha(Files.readAllBytes(file)))
            assertTrue(String(Files.readAllBytes(file)).contains("\"transactions\": []"))
            storage.importSnapshot(snapshot, CurrencyImportMode.REPLACE).toCompletableFuture().join()
            assertEquals(sha(legacyBytes(storage)), sha(Files.readAllBytes(file)))
        }
        val reloaded = Files.readAllBytes(file)
        FileCurrencyStorage(file, 1000).use { storage ->
            storage.transact(currency, descriptor, requests(1).single()).toCompletableFuture().join()
            assertEquals(sha(legacyBytes(storage)), sha(Files.readAllBytes(file)))
        }
        assertTrue(reloaded.isNotEmpty())
    }

    @Test
    fun `a thousand operations encode each transaction once`(@TempDir directory: Path) {
        val encodings = AtomicLong()
        val ledgerSizes = ArrayList<Int>()
        val storage = FileCurrencyStorage(directory.resolve("ledger.json"), 5000, { encodings.incrementAndGet() }) { target, fill ->
            AtomicFiles.write(target, durable = false, fill)
            ledgerSizes += 1
        }
        storage.use {
            requests(1000).forEach { storage.transact(currency, descriptor, it).toCompletableFuture().join() }
            val ledger = storage.export(currency).toCompletableFuture().join().transactions.size
            // before: each of the saves encoded the whole ledger so far; now each transaction once
            val old = (1..ledgerSizes.size).fold(0L) { total, save -> total + minOf(save, ledger) }
            assertEquals(ledger.toLong(), encodings.get())
            assertTrue(old > 100L * encodings.get(), "old=$old new=${encodings.get()}")
        }
    }
}
