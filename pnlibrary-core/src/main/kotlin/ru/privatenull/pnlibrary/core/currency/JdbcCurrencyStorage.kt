package ru.privatenull.pnlibrary.core.currency

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.privatenull.pnlibrary.api.currency.*
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

internal class JdbcCurrencyStorage(
    private val dataSource: DataSource,
    tablePrefix: String,
) : CurrencyStorage {
    private val prefix = tablePrefix.also { require(it.matches(Regex("[A-Za-z0-9_]+"))) { "Invalid currency table prefix" } }
    private val accountsTable = "${prefix}_accounts"
    private val transactionsTable = "${prefix}_transactions"
    private val initialized = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val gson = Gson()
    private val executor = Executors.newFixedThreadPool(2) { task -> Thread(task, "pnLibrary-currency-jdbc").apply { isDaemon = true } }

    override fun balance(currency: CurrencyKey, account: CurrencyAccount): CompletionStage<BigDecimal> = async {
        connection { connection -> readBalance(connection, currency, account, false) }
    }

    override fun transact(currency: CurrencyKey, descriptor: CurrencyDescriptor, request: CurrencyTransactionRequest): CompletionStage<CurrencyTransaction> = async {
        ensureSchema()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            try {
                request.idempotencyKey?.let { findIdempotent(connection, currency, it) }?.let {
                    connection.rollback(); return@use it
                }
                val accounts = listOfNotNull(request.source, request.target).distinctBy { it.playerId }.sortedBy { it.playerId.toString() }
                val balances = accounts.associateWith { readBalance(connection, currency, it, true) }.toMutableMap()
                val sourceBefore = request.source?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
                val targetBefore = request.target?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
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
                    request.source?.let { writeBalance(connection, currency, it, descriptor.normalize(sourceAfter!!)) }
                    request.target?.let { writeBalance(connection, currency, it, descriptor.normalize(targetAfter!!)) }
                }
                val transaction = CurrencyTransaction(
                    UUID.randomUUID(), currency, request.type, status, descriptor.normalize(request.amount), request.source, request.target,
                    request.actor, request.service, request.reason, request.metadata, sourceBefore, sourceAfter, targetBefore, targetAfter,
                    Instant.now(), failure, request.idempotencyKey,
                )
                insertTransaction(connection, transaction)
                connection.commit()
                transaction
            } catch (error: Throwable) {
                runCatching { connection.rollback() }
                val idempotencyKey = request.idempotencyKey
                if (error is SQLException && idempotencyKey != null && error.sqlState?.startsWith("23") == true) {
                    findIdempotent(connection, currency, idempotencyKey)
                        ?: throw error
                } else throw error
            }
        }
    }

    override fun history(currency: CurrencyKey, query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage> = async {
        connection { connection ->
            val clauses = mutableListOf("currency_id = ?")
            val parameters = mutableListOf<Any>(currency.toString())
            query.account?.let { clauses += "(source_account = ? OR target_account = ?)"; parameters += it.toString(); parameters += it.toString() }
            query.actor?.let { clauses += "actor_type = ? AND actor_id = ?"; parameters += it.type.name; parameters += it.id }
            query.service?.let { clauses += "service_id = ?"; parameters += it }
            query.from?.let { clauses += "created_at >= ?"; parameters += Timestamp.from(it) }
            query.until?.let { clauses += "created_at < ?"; parameters += Timestamp.from(it) }
            if (query.types.isNotEmpty()) {
                clauses += "transaction_type IN (${query.types.joinToString(",") { "?" }})"
                parameters.addAll(query.types.map { it.name })
            }
            val sql = "SELECT * FROM $transactionsTable WHERE ${clauses.joinToString(" AND ")} ORDER BY created_at DESC LIMIT ? OFFSET ?"
            connection.prepareStatement(sql).use { statement ->
                (parameters + listOf(query.limit + 1, query.offset)).forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { results ->
                    val values = mutableListOf<CurrencyTransaction>()
                    while (results.next()) values += decode(results)
                    CurrencyHistoryPage(values.take(query.limit), query.offset, values.size > query.limit)
                }
            }
        }
    }

    override fun export(currency: CurrencyKey): CompletionStage<CurrencyStorageSnapshot> = async {
        connection { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            try {
                val balances = linkedMapOf<UUID, BigDecimal>()
                connection.prepareStatement("SELECT account_id, balance FROM $accountsTable WHERE currency_id = ?").use { statement ->
                    statement.setString(1, currency.toString())
                    statement.executeQuery().use { results -> while (results.next()) balances[UUID.fromString(results.getString(1))] = results.getBigDecimal(2) }
                }
                val transactions = mutableListOf<CurrencyTransaction>()
                connection.prepareStatement("SELECT * FROM $transactionsTable WHERE currency_id = ? ORDER BY created_at ASC").use { statement ->
                    statement.setString(1, currency.toString())
                    statement.executeQuery().use { results -> while (results.next()) transactions += decode(results) }
                }
                connection.commit()
                CurrencyStorageSnapshot(currency = currency, createdAt = Instant.now(), balances = balances, transactions = transactions)
            } catch (error: Throwable) {
                runCatching { connection.rollback() }
                throw error
            }
        }
    }

    override fun importSnapshot(snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode): CompletionStage<CurrencyImportResult> = async {
        require(snapshot.transactions.all { it.currency == snapshot.currency }) { "Snapshot contains transactions from another currency" }
        ensureSchema()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            try {
                if (mode == CurrencyImportMode.REPLACE) {
                    connection.prepareStatement("DELETE FROM $transactionsTable WHERE currency_id = ?").use { it.setString(1, snapshot.currency.toString()); it.executeUpdate() }
                    connection.prepareStatement("DELETE FROM $accountsTable WHERE currency_id = ?").use { it.setString(1, snapshot.currency.toString()); it.executeUpdate() }
                }
                var importedAccounts = 0
                var skippedAccounts = 0
                snapshot.balances.forEach { (accountId, balance) ->
                    val account = CurrencyAccount(accountId)
                    val exists = accountExists(connection, snapshot.currency, account)
                    if (exists && mode == CurrencyImportMode.MERGE_KEEP_TARGET) skippedAccounts++
                    else { writeBalance(connection, snapshot.currency, account, balance); importedAccounts++ }
                }
                var importedTransactions = 0
                var skippedTransactions = 0
                snapshot.transactions.forEach { transaction ->
                    if (transactionExists(connection, transaction)) skippedTransactions++
                    else { insertTransaction(connection, transaction); importedTransactions++ }
                }
                connection.commit()
                CurrencyImportResult(snapshot.currency, mode, importedAccounts, skippedAccounts, importedTransactions, skippedTransactions)
            } catch (error: Throwable) {
                runCatching { connection.rollback() }
                throw error
            }
        }
    }

    override fun close() { if (closed.compareAndSet(false, true)) executor.shutdown() }

    private fun ensureSchema() {
        if (initialized.get()) return
        synchronized(initialized) {
            if (initialized.get()) return
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS $accountsTable (currency_id VARCHAR(190) NOT NULL, account_id VARCHAR(36) NOT NULL, balance DECIMAL(38,18) NOT NULL, PRIMARY KEY (currency_id, account_id))")
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS $transactionsTable (transaction_id VARCHAR(36) PRIMARY KEY, currency_id VARCHAR(190) NOT NULL, transaction_type VARCHAR(32) NOT NULL, status VARCHAR(16) NOT NULL, amount DECIMAL(38,18) NOT NULL, source_account VARCHAR(36), target_account VARCHAR(36), actor_type VARCHAR(16) NOT NULL, actor_id VARCHAR(190) NOT NULL, service_id VARCHAR(190) NOT NULL, reason_text TEXT, metadata_text TEXT NOT NULL, source_before DECIMAL(38,18), source_after DECIMAL(38,18), target_before DECIMAL(38,18), target_after DECIMAL(38,18), created_at TIMESTAMP NOT NULL, failure_text TEXT, idempotency_key VARCHAR(190), UNIQUE (currency_id, idempotency_key))")
                }
            }
            initialized.set(true)
        }
    }

    private fun readBalance(connection: Connection, currency: CurrencyKey, account: CurrencyAccount, lock: Boolean): BigDecimal {
        ensureSchema()
        val supportsLock = !connection.metaData.databaseProductName.contains("SQLite", true)
        val sql = "SELECT balance FROM $accountsTable WHERE currency_id = ? AND account_id = ?" + if (lock && supportsLock) " FOR UPDATE" else ""
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, currency.toString()); statement.setString(2, account.playerId.toString())
            statement.executeQuery().use { return if (it.next()) it.getBigDecimal(1) else BigDecimal.ZERO }
        }
    }

    private fun writeBalance(connection: Connection, currency: CurrencyKey, account: CurrencyAccount, balance: BigDecimal) {
        connection.prepareStatement("UPDATE $accountsTable SET balance = ? WHERE currency_id = ? AND account_id = ?").use { statement ->
            statement.setBigDecimal(1, balance); statement.setString(2, currency.toString()); statement.setString(3, account.playerId.toString())
            if (statement.executeUpdate() > 0) return
        }
        try {
            connection.prepareStatement("INSERT INTO $accountsTable (currency_id, account_id, balance) VALUES (?, ?, ?)").use { statement ->
                statement.setString(1, currency.toString()); statement.setString(2, account.playerId.toString()); statement.setBigDecimal(3, balance)
                statement.executeUpdate()
            }
        } catch (error: SQLException) {
            if (error.sqlState?.startsWith("23") != true) throw error
            connection.prepareStatement("UPDATE $accountsTable SET balance = ? WHERE currency_id = ? AND account_id = ?").use { statement ->
                statement.setBigDecimal(1, balance); statement.setString(2, currency.toString()); statement.setString(3, account.playerId.toString()); statement.executeUpdate()
            }
        }
    }

    private fun accountExists(connection: Connection, currency: CurrencyKey, account: CurrencyAccount): Boolean {
        connection.prepareStatement("SELECT 1 FROM $accountsTable WHERE currency_id = ? AND account_id = ?").use { statement ->
            statement.setString(1, currency.toString()); statement.setString(2, account.playerId.toString())
            statement.executeQuery().use { return it.next() }
        }
    }

    private fun transactionExists(connection: Connection, transaction: CurrencyTransaction): Boolean {
        val sql = if (transaction.idempotencyKey == null) {
            "SELECT 1 FROM $transactionsTable WHERE transaction_id = ?"
        } else {
            "SELECT 1 FROM $transactionsTable WHERE transaction_id = ? OR (currency_id = ? AND idempotency_key = ?)"
        }
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, transaction.id.toString())
            transaction.idempotencyKey?.let { statement.setString(2, transaction.currency.toString()); statement.setString(3, it) }
            statement.executeQuery().use { return it.next() }
        }
    }

    private fun insertTransaction(connection: Connection, value: CurrencyTransaction) {
        val sql = "INSERT INTO $transactionsTable (transaction_id,currency_id,transaction_type,status,amount,source_account,target_account,actor_type,actor_id,service_id,reason_text,metadata_text,source_before,source_after,target_before,target_after,created_at,failure_text,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        connection.prepareStatement(sql).use { s ->
            val values = listOf(value.id.toString(), value.currency.toString(), value.type.name, value.status.name, value.amount,
                value.source?.playerId?.toString(), value.target?.playerId?.toString(), value.actor.type.name, value.actor.id,
                value.service, value.reason, gson.toJson(value.metadata), value.sourceBalanceBefore, value.sourceBalanceAfter,
                value.targetBalanceBefore, value.targetBalanceAfter, Timestamp.from(value.createdAt), value.failure, value.idempotencyKey)
            values.forEachIndexed { index, item -> s.setObject(index + 1, item) }
            s.executeUpdate()
        }
    }

    private fun findIdempotent(connection: Connection, currency: CurrencyKey, key: String): CurrencyTransaction? {
        ensureSchema()
        connection.prepareStatement("SELECT * FROM $transactionsTable WHERE currency_id = ? AND idempotency_key = ?").use { statement ->
            statement.setString(1, currency.toString()); statement.setString(2, key)
            statement.executeQuery().use { return if (it.next()) decode(it) else null }
        }
    }

    private fun decode(result: ResultSet): CurrencyTransaction {
        val parts = result.getString("currency_id").split(':', limit = 2)
        fun account(column: String) = result.getString(column)?.let(UUID::fromString)?.let(::CurrencyAccount)
        val metadataType = object : TypeToken<Map<String, String>>() {}.type
        return CurrencyTransaction(
            UUID.fromString(result.getString("transaction_id")), CurrencyKey(PluginId.of(parts[0]), parts[1]),
            CurrencyTransactionType.valueOf(result.getString("transaction_type")), CurrencyTransactionStatus.valueOf(result.getString("status")),
            result.getBigDecimal("amount"), account("source_account"), account("target_account"),
            CurrencyActor(CurrencyActorType.valueOf(result.getString("actor_type")), result.getString("actor_id")),
            result.getString("service_id"), result.getString("reason_text"), gson.fromJson(result.getString("metadata_text"), metadataType),
            result.getBigDecimal("source_before"), result.getBigDecimal("source_after"), result.getBigDecimal("target_before"), result.getBigDecimal("target_after"),
            result.getTimestamp("created_at").toInstant(), result.getString("failure_text"), result.getString("idempotency_key"),
        )
    }

    private fun <T> connection(operation: (Connection) -> T): T { ensureSchema(); return dataSource.connection.use(operation) }
    private fun <T> async(operation: () -> T): CompletionStage<T> {
        check(!closed.get()) { "Currency storage is closed" }
        return CompletableFuture.supplyAsync(operation, executor)
    }
}
