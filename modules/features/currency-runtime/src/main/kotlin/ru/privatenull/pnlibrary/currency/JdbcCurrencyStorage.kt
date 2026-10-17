package ru.privatenull.pnlibrary.currency

import com.google.gson.Gson
import ru.privatenull.pnlibrary.api.currency.CurrencyAccount
import ru.privatenull.pnlibrary.api.currency.CurrencyDescriptor
import ru.privatenull.pnlibrary.api.currency.CurrencyHistoryPage
import ru.privatenull.pnlibrary.api.currency.CurrencyHistoryQuery
import ru.privatenull.pnlibrary.api.currency.CurrencyImportMode
import ru.privatenull.pnlibrary.api.currency.CurrencyImportResult
import ru.privatenull.pnlibrary.api.currency.CurrencyKey
import ru.privatenull.pnlibrary.api.currency.CurrencyStorage
import ru.privatenull.pnlibrary.api.currency.CurrencyStorageSnapshot
import ru.privatenull.pnlibrary.api.currency.CurrencyTransaction
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionRequest
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionStatus
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/** JDBC-backed currency storage with serializable balance mutations and ledger writes. */
internal class JdbcCurrencyStorage(
    private val dataSource: DataSource,
    tablePrefix: String,
) : CurrencyStorage {
    private val schema = JdbcCurrencySchema(tablePrefix)
    private val accountsTable = schema.accountsTable
    private val transactionsTable = schema.transactionsTable
    private val initialized = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val gson = Gson()
    private val decoder = JdbcTransactionDecoder(gson)
    private val executor = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "pnLibrary-currency-jdbc").apply {
            isDaemon = true
        }
    }

    override fun balance(currency: CurrencyKey, account: CurrencyAccount): CompletionStage<BigDecimal> = async {
        connection { connection -> readBalance(connection, currency, account, false) }
    }

    override fun transact(
        currency: CurrencyKey,
        descriptor: CurrencyDescriptor,
        request: CurrencyTransactionRequest,
    ): CompletionStage<CurrencyTransaction> = async {
        ensureSchema()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            try {
                request.idempotencyKey?.let { findIdempotent(connection, currency, it) }?.let {
                    connection.rollback()
                    return@use it
                }
                val accounts = listOfNotNull(request.source, request.target)
                    .distinctBy { it.playerId }
                    .sortedBy { it.playerId.toString() }
                val balances = accounts
                    .associateWith { readBalance(connection, currency, it, true) }
                    .toMutableMap()
                val sourceBefore = request.source?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
                val targetBefore = request.target?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
                val mutation = CurrencyMutationEngine.evaluate(request, sourceBefore, targetBefore)
                if (mutation.status == CurrencyTransactionStatus.COMMITTED) {
                    request.source?.let {
                        writeBalance(connection, currency, it, descriptor.normalize(requireNotNull(mutation.sourceAfter)))
                    }
                    request.target?.let {
                        writeBalance(connection, currency, it, descriptor.normalize(requireNotNull(mutation.targetAfter)))
                    }
                }
                val transaction = CurrencyTransaction(
                    id = UUID.randomUUID(),
                    currency = currency,
                    type = request.type,
                    status = mutation.status,
                    amount = descriptor.normalize(request.amount),
                    source = request.source,
                    target = request.target,
                    actor = request.actor,
                    service = request.service,
                    reason = request.reason,
                    metadata = request.metadata,
                    sourceBalanceBefore = mutation.sourceBefore,
                    sourceBalanceAfter = mutation.sourceAfter,
                    targetBalanceBefore = mutation.targetBefore,
                    targetBalanceAfter = mutation.targetAfter,
                    createdAt = Instant.now(),
                    failure = mutation.failure,
                    idempotencyKey = request.idempotencyKey,
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
            val history = historyStatement(currency, query)
            connection.prepareStatement(history.sql).use { statement ->
                (history.parameters + listOf(query.limit + 1, query.offset)).forEachIndexed { index, value ->
                    statement.setObject(index + 1, value)
                }
                statement.executeQuery().use { results ->
                    val values = mutableListOf<CurrencyTransaction>()
                    while (results.next()) values += decoder.decode(results)
                    CurrencyHistoryPage(
                        items = values.take(query.limit),
                        offset = query.offset,
                        hasMore = values.size > query.limit,
                    )
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
                connection.prepareStatement(
                    "SELECT account_id, balance FROM $accountsTable WHERE currency_id = ?",
                ).use { statement ->
                    statement.setString(1, currency.toString())
                    statement.executeQuery().use { results ->
                        while (results.next()) {
                            balances[UUID.fromString(results.getString(1))] = results.getBigDecimal(2)
                        }
                    }
                }
                val transactions = mutableListOf<CurrencyTransaction>()
                connection.prepareStatement(
                    "SELECT * FROM $transactionsTable WHERE currency_id = ? ORDER BY created_at ASC",
                ).use { statement ->
                    statement.setString(1, currency.toString())
                    statement.executeQuery().use { results ->
                        while (results.next()) {
                            transactions += decoder.decode(results)
                        }
                    }
                }
                connection.commit()
                CurrencyStorageSnapshot(
                    currency = currency,
                    createdAt = Instant.now(),
                    balances = balances,
                    transactions = transactions,
                )
            } catch (error: Throwable) {
                runCatching { connection.rollback() }
                throw error
            }
        }
    }

    override fun importSnapshot(
        snapshot: CurrencyStorageSnapshot,
        mode: CurrencyImportMode,
    ): CompletionStage<CurrencyImportResult> = async {
        require(snapshot.transactions.all { it.currency == snapshot.currency }) {
            "Snapshot contains transactions from another currency"
        }
        ensureSchema()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            try {
                if (mode == CurrencyImportMode.REPLACE) {
                    deleteCurrency(connection, snapshot.currency)
                }
                var importedAccounts = 0
                var skippedAccounts = 0
                snapshot.balances.forEach { (accountId, balance) ->
                    val account = CurrencyAccount(accountId)
                    val exists = accountExists(connection, snapshot.currency, account)
                    if (exists && mode == CurrencyImportMode.MERGE_KEEP_TARGET) {
                        skippedAccounts++
                    } else {
                        writeBalance(connection, snapshot.currency, account, balance)
                        importedAccounts++
                    }
                }
                var importedTransactions = 0
                var skippedTransactions = 0
                snapshot.transactions.forEach { transaction ->
                    if (transactionExists(connection, transaction)) {
                        skippedTransactions++
                    } else {
                        insertTransaction(connection, transaction)
                        importedTransactions++
                    }
                }
                connection.commit()
                CurrencyImportResult(
                    currency = snapshot.currency,
                    mode = mode,
                    importedAccounts = importedAccounts,
                    skippedAccounts = skippedAccounts,
                    importedTransactions = importedTransactions,
                    skippedTransactions = skippedTransactions,
                )
            } catch (error: Throwable) {
                runCatching { connection.rollback() }
                throw error
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) executor.shutdown()
    }

    private fun ensureSchema() {
        if (initialized.get()) return
        synchronized(initialized) {
            if (initialized.get()) return
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(schema.createAccountsTableSql())
                    statement.executeUpdate(schema.createTransactionsTableSql())
                }
            }
            initialized.set(true)
        }
    }

    private fun historyStatement(currency: CurrencyKey, query: CurrencyHistoryQuery): SqlStatement {
        val clauses = mutableListOf("currency_id = ?")
        val parameters = mutableListOf<Any>(currency.toString())

        query.account?.let {
            clauses += "(source_account = ? OR target_account = ?)"
            parameters += it.toString()
            parameters += it.toString()
        }
        query.actor?.let {
            clauses += "actor_type = ? AND actor_id = ?"
            parameters += it.type.name
            parameters += it.id
        }
        query.service?.let {
            clauses += "service_id = ?"
            parameters += it
        }
        query.from?.let {
            clauses += "created_at >= ?"
            parameters += Timestamp.from(it)
        }
        query.until?.let {
            clauses += "created_at < ?"
            parameters += Timestamp.from(it)
        }
        if (query.types.isNotEmpty()) {
            clauses += "transaction_type IN (${query.types.joinToString(",") { "?" }})"
            parameters.addAll(query.types.map { it.name })
        }

        return SqlStatement(
            sql = "SELECT * FROM $transactionsTable WHERE ${clauses.joinToString(" AND ")} " +
                "ORDER BY created_at DESC LIMIT ? OFFSET ?",
            parameters = parameters,
        )
    }

    private fun deleteCurrency(connection: Connection, currency: CurrencyKey) {
        listOf(transactionsTable, accountsTable).forEach { table ->
            connection.prepareStatement("DELETE FROM $table WHERE currency_id = ?").use { statement ->
                statement.setString(1, currency.toString())
                statement.executeUpdate()
            }
        }
    }

    private fun readBalance(
        connection: Connection,
        currency: CurrencyKey,
        account: CurrencyAccount,
        lock: Boolean,
    ): BigDecimal {
        ensureSchema()
        val supportsLock = !connection.metaData.databaseProductName.contains("SQLite", true)
        val lockClause = if (lock && supportsLock) " FOR UPDATE" else ""
        val sql = "SELECT balance FROM $accountsTable " +
            "WHERE currency_id = ? AND account_id = ?$lockClause"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, currency.toString())
            statement.setString(2, account.playerId.toString())
            statement.executeQuery().use { return if (it.next()) it.getBigDecimal(1) else BigDecimal.ZERO }
        }
    }

    private fun writeBalance(
        connection: Connection,
        currency: CurrencyKey,
        account: CurrencyAccount,
        balance: BigDecimal,
    ) {
        connection.prepareStatement(
            "UPDATE $accountsTable SET balance = ? WHERE currency_id = ? AND account_id = ?",
        ).use { statement ->
            statement.setBigDecimal(1, balance)
            statement.setString(2, currency.toString())
            statement.setString(3, account.playerId.toString())
            if (statement.executeUpdate() > 0) return
        }
        try {
            connection.prepareStatement(
                "INSERT INTO $accountsTable (currency_id, account_id, balance) VALUES (?, ?, ?)",
            ).use { statement ->
                statement.setString(1, currency.toString())
                statement.setString(2, account.playerId.toString())
                statement.setBigDecimal(3, balance)
                statement.executeUpdate()
            }
        } catch (error: SQLException) {
            if (error.sqlState?.startsWith("23") != true) throw error
            connection.prepareStatement(
                "UPDATE $accountsTable SET balance = ? WHERE currency_id = ? AND account_id = ?",
            ).use { statement ->
                statement.setBigDecimal(1, balance)
                statement.setString(2, currency.toString())
                statement.setString(3, account.playerId.toString())
                statement.executeUpdate()
            }
        }
    }

    private fun accountExists(connection: Connection, currency: CurrencyKey, account: CurrencyAccount): Boolean {
        connection.prepareStatement(
            "SELECT 1 FROM $accountsTable WHERE currency_id = ? AND account_id = ?",
        ).use { statement ->
            statement.setString(1, currency.toString())
            statement.setString(2, account.playerId.toString())
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
            transaction.idempotencyKey?.let {
                statement.setString(2, transaction.currency.toString())
                statement.setString(3, it)
            }
            statement.executeQuery().use { return it.next() }
        }
    }

    private fun insertTransaction(connection: Connection, value: CurrencyTransaction) {
        connection.prepareStatement(schema.insertTransactionSql()).use { statement ->
            val values = listOf(
                value.id.toString(),
                value.currency.toString(),
                value.type.name,
                value.status.name,
                value.amount,
                value.source?.playerId?.toString(),
                value.target?.playerId?.toString(),
                value.actor.type.name,
                value.actor.id,
                value.service,
                value.reason,
                gson.toJson(value.metadata),
                value.sourceBalanceBefore,
                value.sourceBalanceAfter,
                value.targetBalanceBefore,
                value.targetBalanceAfter,
                Timestamp.from(value.createdAt),
                value.failure,
                value.idempotencyKey,
            )
            values.forEachIndexed { index, item -> statement.setObject(index + 1, item) }
            statement.executeUpdate()
        }
    }

    private fun findIdempotent(connection: Connection, currency: CurrencyKey, key: String): CurrencyTransaction? {
        ensureSchema()
        connection.prepareStatement(
            "SELECT * FROM $transactionsTable WHERE currency_id = ? AND idempotency_key = ?",
        ).use { statement ->
            statement.setString(1, currency.toString())
            statement.setString(2, key)
            statement.executeQuery().use { return if (it.next()) decoder.decode(it) else null }
        }
    }

    private fun <T> connection(operation: (Connection) -> T): T {
        ensureSchema()
        return dataSource.connection.use(operation)
    }

    private fun <T> async(operation: () -> T): CompletionStage<T> {
        check(!closed.get()) { "Currency storage is closed" }
        return try {
            CompletableFuture.supplyAsync(operation, executor)
        } catch (error: RejectedExecutionException) {
            CompletableFuture<T>().also { it.completeExceptionally(error) }
        }
    }

    private data class SqlStatement(
        val sql: String,
        val parameters: List<Any>,
    )
}
