package ru.privatenull.pnlibrary.core.currency

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.currency.*
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class FileCurrencyStorage(
    private val file: Path,
    private val maximumTransactions: Int,
) : CurrencyStorage {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "pnLibrary-currency-file").apply { isDaemon = true }
    }
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

    override fun transact(currency: CurrencyKey, descriptor: CurrencyDescriptor, request: CurrencyTransactionRequest): CompletionStage<CurrencyTransaction> = async {
        synchronized(lock) {
            request.idempotencyKey?.let { idempotency[idempotencyKey(currency, it)] }?.let { return@synchronized it }
            val affectedKeys = listOfNotNull(
                request.source?.let { accountKey(currency, it) },
                request.target?.let { accountKey(currency, it) },
            ).distinct()
            val balancesBefore = affectedKeys.associateWith { balances[it] }
            val transaction = apply(currency, descriptor, request)
            transactions += transaction
            request.idempotencyKey?.let { idempotency[idempotencyKey(currency, it)] = transaction }
            val evicted = mutableListOf<CurrencyTransaction>()
            while (transactions.size > maximumTransactions) {
                val removed = transactions.removeAt(0).also(evicted::add)
                removed.idempotencyKey?.let { idempotency.remove(idempotencyKey(removed.currency, it), removed) }
            }
            try {
                save()
            } catch (error: Throwable) {
                balancesBefore.forEach { (key, value) -> if (value == null) balances.remove(key) else balances[key] = value }
                transactions.remove(transaction)
                transactions.addAll(0, evicted)
                request.idempotencyKey?.let { idempotency.remove(idempotencyKey(currency, it), transaction) }
                evicted.forEach { old -> old.idempotencyKey?.let { idempotency[idempotencyKey(old.currency, it)] = old } }
                throw error
            }
            transaction
        }
    }

    override fun history(currency: CurrencyKey, query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage> = async {
        val filtered = synchronized(lock) { transactions.asReversed().filter { transaction ->
            transaction.currency == currency &&
                (query.account == null || transaction.source?.playerId == query.account || transaction.target?.playerId == query.account) &&
                (query.actor == null || transaction.actor == query.actor) &&
                (query.service == null || transaction.service.equals(query.service, true)) &&
                (query.types.isEmpty() || transaction.type in query.types) &&
                (query.from == null || !transaction.createdAt.isBefore(query.from)) &&
                (query.until == null || transaction.createdAt.isBefore(query.until))
        } }
        CurrencyHistoryPage(filtered.drop(query.offset).take(query.limit), query.offset, filtered.size > query.offset + query.limit)
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
            val balancesBackup = LinkedHashMap(balances)
            val transactionsBackup = transactions.toList()
            val idempotencyBackup = HashMap(idempotency)
            try {
                if (mode == CurrencyImportMode.REPLACE) removeCurrency(snapshot.currency)
                var importedAccounts = 0
                var skippedAccounts = 0
                var importedTransactions = 0
                var skippedTransactions = 0
                snapshot.balances.forEach { (accountId, balance) ->
                    val key = accountKey(snapshot.currency, CurrencyAccount(accountId))
                    if (mode == CurrencyImportMode.MERGE_KEEP_TARGET && balances.containsKey(key)) skippedAccounts++
                    else { balances[key] = balance; importedAccounts++ }
                }
                val ids = transactions.mapTo(hashSetOf()) { it.id }
                snapshot.transactions.forEach { transaction ->
                    val duplicateKey = transaction.idempotencyKey?.let { idempotencyKey(snapshot.currency, it) }
                    if (transaction.id in ids || duplicateKey != null && duplicateKey in idempotency) {
                        skippedTransactions++
                    } else {
                        transactions += transaction
                        ids += transaction.id
                        duplicateKey?.let { idempotency[it] = transaction }
                        importedTransactions++
                    }
                }
                while (transactions.size > maximumTransactions) transactions.removeAt(0).also { removed ->
                    removed.idempotencyKey?.let { idempotency.remove(idempotencyKey(removed.currency, it), removed) }
                }
                save()
                CurrencyImportResult(snapshot.currency, mode, importedAccounts, skippedAccounts, importedTransactions, skippedTransactions)
            } catch (error: Throwable) {
                balances.clear(); balances.putAll(balancesBackup)
                transactions.clear(); transactions.addAll(transactionsBackup)
                idempotency.clear(); idempotency.putAll(idempotencyBackup)
                throw error
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) executor.shutdown()
    }

    private fun apply(currency: CurrencyKey, descriptor: CurrencyDescriptor, request: CurrencyTransactionRequest): CurrencyTransaction {
        val sourceKey = request.source?.let { accountKey(currency, it) }
        val targetKey = request.target?.let { accountKey(currency, it) }
        val sourceBefore = sourceKey?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
        val targetBefore = targetKey?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
        var sourceAfter = sourceBefore
        var targetAfter = targetBefore
        var status = CurrencyTransactionStatus.COMMITTED
        var failure: String? = null
        when (request.type) {
            CurrencyTransactionType.CREDIT -> targetAfter = targetBefore!! + request.amount
            CurrencyTransactionType.DEBIT -> if (sourceBefore!! < request.amount) {
                status = CurrencyTransactionStatus.REJECTED; failure = "Insufficient funds"
            } else sourceAfter = sourceBefore - request.amount
            CurrencyTransactionType.TRANSFER -> if (sourceBefore!! < request.amount) {
                status = CurrencyTransactionStatus.REJECTED; failure = "Insufficient funds"
            } else {
                sourceAfter = sourceBefore - request.amount
                targetAfter = targetBefore!! + request.amount
            }
            CurrencyTransactionType.SET_BALANCE -> targetAfter = request.amount
            CurrencyTransactionType.RESET -> targetAfter = BigDecimal.ZERO
        }
        if (status == CurrencyTransactionStatus.COMMITTED) {
            sourceKey?.let { balances[it] = descriptor.normalize(sourceAfter!!) }
            targetKey?.let { balances[it] = descriptor.normalize(targetAfter!!) }
        }
        return CurrencyTransaction(
            UUID.randomUUID(), currency, request.type, status, descriptor.normalize(request.amount),
            request.source, request.target, request.actor, request.service, request.reason, request.metadata,
            sourceBefore, sourceAfter, targetBefore, targetAfter, Instant.now(), failure, request.idempotencyKey,
        )
    }

    private fun load() {
        if (!Files.exists(file)) return
        require(!Files.isSymbolicLink(file)) { "Currency storage file must not be a symbolic link" }
        val root = Files.newBufferedReader(file, StandardCharsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        root.getAsJsonObject("balances")?.entrySet()?.forEach { balances[it.key] = it.value.asString.toBigDecimal() }
        root.getAsJsonArray("transactions")?.forEach { element ->
            decode(element.asJsonObject).also { transaction ->
                transactions += transaction
                transaction.idempotencyKey?.let { idempotency[idempotencyKey(transaction.currency, it)] = transaction }
            }
        }
    }

    private fun save() {
        file.parent?.let(Files::createDirectories)
        require(!Files.exists(file) || !Files.isSymbolicLink(file)) { "Currency storage file must not be a symbolic link" }
        val root = JsonObject()
        root.add("balances", JsonObject().also { values -> balances.forEach { (key, value) -> values.addProperty(key, value.toPlainString()) } })
        root.add("transactions", JsonArray().also { values -> transactions.forEach { values.add(encode(it)) } })
        val parent = file.toAbsolutePath().parent
        val temporary = Files.createTempFile(parent, file.fileName.toString(), ".tmp")
        try {
            Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { gson.toJson(root, it) }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun encode(value: CurrencyTransaction) = JsonObject().apply {
        addProperty("id", value.id.toString()); addProperty("currency", value.currency.toString())
        addProperty("type", value.type.name); addProperty("status", value.status.name); addProperty("amount", value.amount.toPlainString())
        value.source?.let { addProperty("source", it.playerId.toString()) }; value.target?.let { addProperty("target", it.playerId.toString()) }
        addProperty("actorType", value.actor.type.name); addProperty("actorId", value.actor.id); addProperty("service", value.service)
        value.reason?.let { addProperty("reason", it) }; value.failure?.let { addProperty("failure", it) }; value.idempotencyKey?.let { addProperty("idempotencyKey", it) }
        addProperty("createdAt", value.createdAt.toString())
        value.sourceBalanceBefore?.let { addProperty("sourceBalanceBefore", it.toPlainString()) }; value.sourceBalanceAfter?.let { addProperty("sourceBalanceAfter", it.toPlainString()) }
        value.targetBalanceBefore?.let { addProperty("targetBalanceBefore", it.toPlainString()) }; value.targetBalanceAfter?.let { addProperty("targetBalanceAfter", it.toPlainString()) }
        add("metadata", JsonObject().also { objectValue -> value.metadata.forEach { (key, entry) -> objectValue.addProperty(key, entry) } })
    }

    private fun decode(value: JsonObject): CurrencyTransaction {
        val currencyParts = value["currency"].asString.split(':', limit = 2)
        fun decimal(name: String) = value.get(name)?.takeUnless { it.isJsonNull }?.asString?.toBigDecimal()
        fun account(name: String) = value.get(name)?.takeUnless { it.isJsonNull }?.asString?.let(UUID::fromString)?.let(::CurrencyAccount)
        val metadata = value.getAsJsonObject("metadata")?.entrySet()?.associate { it.key to it.value.asString }.orEmpty()
        return CurrencyTransaction(
            UUID.fromString(value["id"].asString), CurrencyKey(PluginId.of(currencyParts[0]), currencyParts[1]),
            CurrencyTransactionType.valueOf(value["type"].asString), CurrencyTransactionStatus.valueOf(value["status"].asString),
            value["amount"].asString.toBigDecimal(), account("source"), account("target"),
            CurrencyActor(CurrencyActorType.valueOf(value["actorType"].asString), value["actorId"].asString),
            value["service"].asString, value.get("reason")?.asString, metadata,
            decimal("sourceBalanceBefore"), decimal("sourceBalanceAfter"), decimal("targetBalanceBefore"), decimal("targetBalanceAfter"),
            Instant.parse(value["createdAt"].asString), value.get("failure")?.asString, value.get("idempotencyKey")?.asString,
        )
    }

    private fun accountKey(currency: CurrencyKey, account: CurrencyAccount) = "$currency|${account.playerId}"
    private fun idempotencyKey(currency: CurrencyKey, key: String) = "$currency|$key"
    private fun removeCurrency(currency: CurrencyKey) {
        val prefix = "$currency|"
        balances.keys.removeIf { it.startsWith(prefix) }
        transactions.removeIf { it.currency == currency }
        idempotency.keys.removeIf { it.startsWith(prefix) }
    }
    private fun <T> async(operation: () -> T): CompletionStage<T> {
        check(!closed.get()) { "Currency storage is closed" }
        return CompletableFuture.supplyAsync(operation, executor)
    }
}
