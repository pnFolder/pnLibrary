package org.pnfolder.pnlibrary.currency

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
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
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-writer JSON storage with group commit: transactions that wait together are applied one after another and saved
 * once. Each result is reported only after the file holding it has reached the disk.
 */
internal class FileCurrencyStorage(
    private val file: Path,
    private val maximumTransactions: Int,
    private val replaceFile: (Path, (Path) -> Unit) -> Unit = { target, fill -> AtomicFiles.write(target, durable = true, fill) },
) : CurrencyStorage {
    private val worker = ManagedWorker("pnLibrary-currency-file")
    private val state = CurrencyLedgerState()
    private val jobs = ConcurrentLinkedQueue<Job>()
    private val drainScheduled = AtomicBoolean()
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    init {
        require(maximumTransactions in 100..1_000_000) { "maximumTransactions must be between 100 and 1000000" }
        load()
    }

    override fun balance(currency: CurrencyKey, account: CurrencyAccount): CompletionStage<BigDecimal> = async {
        state.balances[CurrencyLedgerState.accountKey(currency, account)] ?: BigDecimal.ZERO
    }

    override fun transact(
        currency: CurrencyKey,
        descriptor: CurrencyDescriptor,
        request: CurrencyTransactionRequest,
    ): CompletionStage<CurrencyTransaction> {
        val job = Job.Transact(currency, descriptor, request)
        enqueue(job)
        return job.result
    }

    override fun history(currency: CurrencyKey, query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage> = async {
        val filtered = state.transactions.asReversed().filter { transaction -> matches(transaction, currency, query) }
        CurrencyHistoryPage(
            items = filtered.drop(query.offset).take(query.limit),
            offset = query.offset,
            hasMore = filtered.size > query.offset + query.limit,
        )
    }

    override fun export(currency: CurrencyKey): CompletionStage<CurrencyStorageSnapshot> = async {
        val prefix = "$currency|"
        val exportedBalances = state.balances.entries.filter { it.key.startsWith(prefix) }.associate {
            UUID.fromString(it.key.removePrefix(prefix)) to it.value
        }
        CurrencyStorageSnapshot(
            currency = currency,
            createdAt = Instant.now(),
            balances = exportedBalances,
            transactions = state.transactions.filter { it.currency == currency },
        )
    }

    override fun importSnapshot(snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode): CompletionStage<CurrencyImportResult> = async {
        require(snapshot.transactions.all { it.currency == snapshot.currency }) { "Snapshot contains transactions from another currency" }
        replacingState {
            if (mode == CurrencyImportMode.REPLACE) state.removeCurrency(snapshot.currency)
            val accounts = importBalances(snapshot, mode)
            val moved = importTransactions(snapshot)
            state.trim(maximumTransactions)
            CurrencyImportResult(
                currency = snapshot.currency,
                mode = mode,
                importedAccounts = accounts.first,
                skippedAccounts = accounts.second,
                importedTransactions = moved.first,
                skippedTransactions = moved.second,
            )
        }
    }

    /** Returns how many accounts were imported and how many were skipped because the target already had them. */
    private fun importBalances(snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode): Pair<Int, Int> {
        var imported = 0
        var skipped = 0
        snapshot.balances.forEach { (accountId, balance) ->
            val key = CurrencyLedgerState.accountKey(snapshot.currency, CurrencyAccount(accountId))
            if (mode == CurrencyImportMode.MERGE_KEEP_TARGET && state.balances.containsKey(key)) {
                skipped++
            } else {
                state.balances[key] = balance
                imported++
            }
        }
        return imported to skipped
    }

    /** Returns how many transactions were imported and how many were skipped as duplicates. */
    private fun importTransactions(snapshot: CurrencyStorageSnapshot): Pair<Int, Int> {
        var imported = 0
        var skipped = 0
        val ids = state.transactions.mapTo(hashSetOf()) { it.id }
        snapshot.transactions.forEach { transaction ->
            val duplicate = transaction.idempotencyKey?.let { state.hasIdempotencyKey(snapshot.currency, it) } == true
            if (transaction.id in ids || duplicate) {
                skipped++
            } else {
                state.record(transaction)
                ids += transaction.id
                imported++
            }
        }
        return imported to skipped
    }

    override fun close() {
        worker.close()
    }

    /** Reads balances and ledger from [file]; a missing file means an empty store. */
    private fun load() {
        if (!Files.exists(file)) return
        require(!Files.isSymbolicLink(file)) { "Currency storage file must not be a symbolic link" }
        val root = Files.newBufferedReader(file, StandardCharsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        root.getAsJsonObject("balances")?.entrySet()?.forEach { state.balances[it.key] = it.value.asString.toBigDecimal() }
        root.getAsJsonArray("transactions")?.forEach { element ->
            state.record(CurrencyTransactionJson.decode(element.asJsonObject))
        }
    }

    /** Applies [change] and saves; on any failure memory goes back to what the file still holds. */
    private fun <T> replacingState(change: () -> T): T {
        val before = state.copy()
        try {
            return change().also { save() }
        } catch (error: Throwable) {
            state.restore(before)
            throw error
        }
    }

    /** Queues [job] for the worker; one drain run takes everything that waits, so jobs keep their arrival order. */
    private fun enqueue(job: Job) {
        jobs.add(job)
        if (!drainScheduled.compareAndSet(false, true)) return
        try {
            worker.submit(::drain).whenComplete { _, error -> if (error != null) failWaiting(error) }
        } catch (error: Throwable) {
            drainScheduled.set(false)
            // A drain that already took the job will still answer it.
            if (jobs.remove(job)) throw error
        }
    }

    private fun drain() {
        drainScheduled.set(false)
        while (true) {
            val first = jobs.poll() ?: return
            if (first is Job.Other) {
                first.run()
                continue
            }
            val batch = arrayListOf(first as Job.Transact)
            while (batch.size < MAXIMUM_BATCH && jobs.peek() is Job.Transact) batch += jobs.poll() as Job.Transact
            commit(batch)
        }
    }

    /** Applies [batch] in order and saves once; if that save fails, each transaction gets its own save so only the failing ones fail. */
    private fun commit(batch: List<Job.Transact>) {
        val undo = LedgerUndo()
        val waiting = arrayListOf<Pair<Job.Transact, CurrencyTransaction>>()
        val unsaved = hashSetOf<UUID>()
        batch.forEach { job ->
            val replay = state.replay(job.currency, job.request)
            if (replay != null && replay.id !in unsaved) return@forEach job.finish(replay)
            val mark = undo.mark()
            val transaction = try {
                replay ?: state.apply(job.currency, job.descriptor, job.request, maximumTransactions, undo)
            } catch (error: Throwable) {
                undo.revertTo(mark)
                return@forEach job.fail(error)
            }
            unsaved += transaction.id
            waiting += job to transaction
        }
        if (waiting.isEmpty()) return
        try {
            save()
        } catch (error: Throwable) {
            undo.revert()
            if (waiting.size == 1) waiting.single().first.fail(error) else waiting.forEach { commit(listOf(it.first)) }
            return
        }
        waiting.forEach { (job, transaction) -> job.finish(transaction) }
    }

    /** Fails everything still queued when the worker gives up, so no caller waits forever. */
    private fun failWaiting(error: Throwable) {
        drainScheduled.set(false)
        while (true) (jobs.poll() ?: return).fail(error)
    }

    // Balances are money: the file must reach the disk before it replaces the previous one, or a power loss
    // after the rename can leave an empty file.
    private fun save() {
        require(!Files.isSymbolicLink(file)) { "Currency storage file must not be a symbolic link" }
        val root = JsonObject()
        root.add("balances", JsonObject().also { values -> state.balances.forEach { (key, value) -> values.addProperty(key, value.toPlainString()) } })
        root.add("transactions", JsonArray().also { values -> state.transactions.forEach { values.add(CurrencyTransactionJson.encode(it)) } })
        replaceFile(file) { temporary ->
            Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { gson.toJson(root, it) }
        }
    }

    /** Applies every non-null filter of [query] with logical AND. */
    private fun matches(
        transaction: CurrencyTransaction,
        currency: CurrencyKey,
        query: CurrencyHistoryQuery,
    ): Boolean = transaction.currency == currency &&
        (query.account == null || transaction.source?.playerId == query.account || transaction.target?.playerId == query.account) &&
        (query.actor == null || transaction.actor == query.actor) &&
        (query.service == null || transaction.service.equals(query.service, true)) &&
        (query.types.isEmpty() || transaction.type in query.types) &&
        (query.from == null || !transaction.createdAt.isBefore(query.from)) &&
        (query.until == null || transaction.createdAt.isBefore(query.until))

    /** Queues [operation] on the single storage worker so file access never blocks the caller. */
    private fun <T> async(operation: () -> T): CompletionStage<T> {
        val result = CompletableFuture<T>()
        enqueue(Job.Other(
            action = {
                try {
                    result.complete(operation())
                } catch (error: Throwable) {
                    result.completeExceptionally(error)
                }
            },
            onFail = { error -> result.completeExceptionally(error) },
        ))
        return result
    }

    private sealed interface Job {
        fun fail(error: Throwable)

        class Transact(
            val currency: CurrencyKey,
            val descriptor: CurrencyDescriptor,
            val request: CurrencyTransactionRequest,
        ) : Job {
            val result = CompletableFuture<CurrencyTransaction>()

            fun finish(transaction: CurrencyTransaction) {
                result.complete(transaction)
            }

            override fun fail(error: Throwable) {
                result.completeExceptionally(error)
            }
        }

        class Other(private val action: () -> Unit, private val onFail: (Throwable) -> Unit) : Job {
            fun run() = action()

            override fun fail(error: Throwable) = onFail(error)
        }
    }

    private companion object {
        const val MAXIMUM_BATCH = 500
    }
}
