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
import java.util.concurrent.CompletionStage

/** Single-writer JSON storage: every change is kept in memory only after the whole file has been saved. */
internal class FileCurrencyStorage(
    private val file: Path,
    private val maximumTransactions: Int,
) : CurrencyStorage {
    private val lock = Any()
    private val worker = ManagedWorker("pnLibrary-currency-file")
    private val balances = linkedMapOf<String, BigDecimal>()
    private val transactions = mutableListOf<CurrencyTransaction>()
    private val idempotency = hashMapOf<String, CurrencyTransaction>()
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    init {
        require(maximumTransactions in 100..1_000_000) { "maximumTransactions must be between 100 and 1000000" }
        load()
    }

    override fun balance(currency: CurrencyKey, account: CurrencyAccount): CompletionStage<BigDecimal> = async {
        synchronized(lock) { balances[accountKey(currency, account)] ?: BigDecimal.ZERO }
    }

    override fun transact(
        currency: CurrencyKey,
        descriptor: CurrencyDescriptor,
        request: CurrencyTransactionRequest,
    ): CompletionStage<CurrencyTransaction> = async {
        synchronized(lock) {
            request.idempotencyKey?.let { idempotency[idempotencyKey(currency, it)] }?.let { return@synchronized it }
            atomically {
                apply(currency, descriptor, request).also { transaction ->
                    record(transaction)
                    trimHistory()
                }
            }
        }
    }

    override fun history(currency: CurrencyKey, query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage> = async {
        val filtered = synchronized(lock) {
            transactions.asReversed().filter { transaction -> matches(transaction, currency, query) }
        }
        CurrencyHistoryPage(
            items = filtered.drop(query.offset).take(query.limit),
            offset = query.offset,
            hasMore = filtered.size > query.offset + query.limit,
        )
    }

    override fun export(currency: CurrencyKey): CompletionStage<CurrencyStorageSnapshot> = async {
        synchronized(lock) {
            val prefix = "$currency|"
            val exportedBalances = balances.entries.filter { it.key.startsWith(prefix) }.associate {
                UUID.fromString(it.key.removePrefix(prefix)) to it.value
            }
            CurrencyStorageSnapshot(
                currency = currency,
                createdAt = Instant.now(),
                balances = exportedBalances,
                transactions = transactions.filter { it.currency == currency },
            )
        }
    }

    override fun importSnapshot(snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode): CompletionStage<CurrencyImportResult> = async {
        synchronized(lock) {
            require(snapshot.transactions.all { it.currency == snapshot.currency }) { "Snapshot contains transactions from another currency" }
            atomically {
                if (mode == CurrencyImportMode.REPLACE) removeCurrency(snapshot.currency)
                val accounts = importBalances(snapshot, mode)
                val moved = importTransactions(snapshot)
                trimHistory()
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
    }

    /** Returns how many accounts were imported and how many were skipped because the target already had them. */
    private fun importBalances(snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode): Pair<Int, Int> {
        var imported = 0
        var skipped = 0
        snapshot.balances.forEach { (accountId, balance) ->
            val key = accountKey(snapshot.currency, CurrencyAccount(accountId))
            if (mode == CurrencyImportMode.MERGE_KEEP_TARGET && balances.containsKey(key)) {
                skipped++
            } else {
                balances[key] = balance
                imported++
            }
        }
        return imported to skipped
    }

    /** Returns how many transactions were imported and how many were skipped as duplicates. */
    private fun importTransactions(snapshot: CurrencyStorageSnapshot): Pair<Int, Int> {
        var imported = 0
        var skipped = 0
        val ids = transactions.mapTo(hashSetOf()) { it.id }
        snapshot.transactions.forEach { transaction ->
            val duplicateKey = transaction.idempotencyKey?.let { idempotencyKey(snapshot.currency, it) }
            if (transaction.id in ids || duplicateKey != null && duplicateKey in idempotency) {
                skipped++
            } else {
                record(transaction)
                ids += transaction.id
                imported++
            }
        }
        return imported to skipped
    }

    override fun close() {
        worker.close()
    }

    private fun apply(currency: CurrencyKey, descriptor: CurrencyDescriptor, request: CurrencyTransactionRequest): CurrencyTransaction {
        val sourceKey = request.source?.let { accountKey(currency, it) }
        val targetKey = request.target?.let { accountKey(currency, it) }
        val sourceBefore = sourceKey?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
        val targetBefore = targetKey?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
        val mutation = CurrencyMutationEngine.evaluate(request, sourceBefore, targetBefore, descriptor)
        if (mutation.committed) {
            sourceKey?.let { balances[it] = descriptor.normalize(requireNotNull(mutation.sourceAfter)) }
            targetKey?.let { balances[it] = descriptor.normalize(requireNotNull(mutation.targetAfter)) }
        }
        return mutation.transaction(currency, descriptor, request)
    }

    private fun load() {
        if (!Files.exists(file)) return
        require(!Files.isSymbolicLink(file)) { "Currency storage file must not be a symbolic link" }
        val root = Files.newBufferedReader(file, StandardCharsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        root.getAsJsonObject("balances")?.entrySet()?.forEach { balances[it.key] = it.value.asString.toBigDecimal() }
        root.getAsJsonArray("transactions")?.forEach { element ->
            record(CurrencyTransactionJson.decode(element.asJsonObject))
        }
    }

    /** Applies [change] and saves; on any failure memory goes back to what the file still holds. */
    private fun <T> atomically(change: () -> T): T {
        val before = snapshotState()
        try {
            return change().also { save() }
        } catch (error: Throwable) {
            restore(before)
            throw error
        }
    }

    private fun record(transaction: CurrencyTransaction) {
        transactions += transaction
        transaction.idempotencyKey?.let { idempotency[idempotencyKey(transaction.currency, it)] = transaction }
    }

    private fun trimHistory() {
        val excess = transactions.size - maximumTransactions
        if (excess <= 0) return
        val evicted = transactions.subList(0, excess)
        evicted.forEach { old -> old.idempotencyKey?.let { idempotency.remove(idempotencyKey(old.currency, it), old) } }
        evicted.clear()
    }

    // Balances are money: the file must reach the disk before it replaces the previous one, or a power loss
    // after the rename can leave an empty file.
    private fun save() {
        require(!Files.isSymbolicLink(file)) { "Currency storage file must not be a symbolic link" }
        val root = JsonObject()
        root.add("balances", JsonObject().also { values -> balances.forEach { (key, value) -> values.addProperty(key, value.toPlainString()) } })
        root.add("transactions", JsonArray().also { values -> transactions.forEach { values.add(CurrencyTransactionJson.encode(it)) } })
        AtomicFiles.write(file, durable = true) { temporary ->
            Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { gson.toJson(root, it) }
        }
    }

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

    private fun snapshotState() = StorageState(
        balances = LinkedHashMap(balances),
        transactions = transactions.toList(),
        idempotency = HashMap(idempotency),
    )

    private fun restore(state: StorageState) {
        balances.clear()
        balances.putAll(state.balances)
        transactions.clear()
        transactions.addAll(state.transactions)
        idempotency.clear()
        idempotency.putAll(state.idempotency)
    }

    private fun accountKey(currency: CurrencyKey, account: CurrencyAccount) = "$currency|${account.playerId}"

    private fun idempotencyKey(currency: CurrencyKey, key: String) = "$currency|$key"

    private fun removeCurrency(currency: CurrencyKey) {
        val prefix = "$currency|"
        balances.keys.removeIf { it.startsWith(prefix) }
        transactions.removeIf { it.currency == currency }
        idempotency.keys.removeIf { it.startsWith(prefix) }
    }

    private fun <T> async(operation: () -> T): CompletionStage<T> = worker.submit(operation)

    private data class StorageState(
        val balances: Map<String, BigDecimal>,
        val transactions: List<CurrencyTransaction>,
        val idempotency: Map<String, CurrencyTransaction>,
    )
}
