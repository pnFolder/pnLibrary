package org.pnfolder.pnlibrary.currency

import com.google.gson.Gson
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
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/**
 * JDBC-backed currency storage with serializable balance mutations and ledger writes.
 *
 * Owns the connection, transaction, schema-initialization, and async plumbing; row SQL lives in
 * [JdbcCurrencyLedger], history paging in [JdbcHistoryQuery], and snapshots in [JdbcCurrencySnapshots].
 */
internal class JdbcCurrencyStorage(
    private val dataSource: DataSource,
    tablePrefix: String,
) : CurrencyStorage {
    private val schema = JdbcCurrencySchema(tablePrefix)
    private val initialized = AtomicBoolean(false)
    private val gson = Gson()
    private val decoder = JdbcTransactionDecoder(gson)
    private val ledger = JdbcCurrencyLedger(schema, gson, decoder)
    private val historyQuery = JdbcHistoryQuery(schema, decoder)
    private val snapshots = JdbcCurrencySnapshots(schema, ledger, decoder)
    private val worker = ManagedWorker("pnLibrary-currency-jdbc", parallelism = 2)

    override fun balance(currency: CurrencyKey, account: CurrencyAccount): CompletionStage<BigDecimal> = async {
        connection { connection -> ledger.readBalance(connection, currency, account, false) }
    }

    override fun transact(
        currency: CurrencyKey,
        descriptor: CurrencyDescriptor,
        request: CurrencyTransactionRequest,
    ): CompletionStage<CurrencyTransaction> = async {
        ensureSchema()
        retryOnSerializationConflict { transactOnce(currency, descriptor, request) }
    }

    /** Runs one SERIALIZABLE attempt; [transact] retries it when the database reports a conflict. */
    private fun transactOnce(
        currency: CurrencyKey,
        descriptor: CurrencyDescriptor,
        request: CurrencyTransactionRequest,
    ): CurrencyTransaction = dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            try {
                request.idempotencyKey?.let { ledger.findIdempotent(connection, currency, it) }?.let {
                    connection.rollback()
                    return@use it
                }
                val accounts = listOfNotNull(request.source, request.target)
                    .distinctBy { it.playerId }
                    .sortedBy { it.playerId.toString() }
                val balances = accounts
                    .associateWith { ledger.readBalance(connection, currency, it, true) }
                    .toMutableMap()
                val sourceBefore = request.source?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
                val targetBefore = request.target?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
                val mutation = CurrencyMutationEngine.evaluate(request, sourceBefore, targetBefore)
                if (mutation.status == CurrencyTransactionStatus.COMMITTED) {
                    request.source?.let {
                        ledger.writeBalance(connection, currency, it, descriptor.normalize(requireNotNull(mutation.sourceAfter)))
                    }
                    request.target?.let {
                        ledger.writeBalance(connection, currency, it, descriptor.normalize(requireNotNull(mutation.targetAfter)))
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
                ledger.insertTransaction(connection, transaction)
                connection.commit()
                transaction
            } catch (error: Throwable) {
                runCatching { connection.rollback() }
                val idempotencyKey = request.idempotencyKey
                if (error is SQLException && idempotencyKey != null && error.sqlState?.startsWith("23") == true) {
                    ledger.findIdempotent(connection, currency, idempotencyKey)
                        ?: throw error
                } else throw error
            }
        }

    /**
     * Retries [block] when PostgreSQL or MySQL reports a serialization failure or deadlock.
     * Gives up after three attempts and rethrows the last error.
     */
    private fun <T> retryOnSerializationConflict(block: () -> T): T {
        val maxAttempts = 3
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (error: SQLException) {
                val conflict = error.sqlState == "40001" || error.sqlState == "40P01"
                if (!conflict || attempt >= maxAttempts) throw error
                attempt++
            }
        }
    }

    override fun history(currency: CurrencyKey, query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage> = async {
        connection { connection -> historyQuery.page(connection, currency, query) }
    }

    override fun export(currency: CurrencyKey): CompletionStage<CurrencyStorageSnapshot> = async {
        connection { connection ->
            committed(connection, Connection.TRANSACTION_REPEATABLE_READ) { snapshots.export(connection, currency) }
        }
    }

    override fun importSnapshot(
        snapshot: CurrencyStorageSnapshot,
        mode: CurrencyImportMode,
    ): CompletionStage<CurrencyImportResult> = async {
        require(snapshot.transactions.all { it.currency == snapshot.currency }) {
            "Snapshot contains transactions from another currency"
        }
        connection { connection ->
            committed(connection, Connection.TRANSACTION_SERIALIZABLE) { snapshots.import(connection, snapshot, mode) }
        }
    }

    override fun close() {
        worker.close()
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

    private fun <T> connection(operation: (Connection) -> T): T {
        ensureSchema()
        return dataSource.connection.use(operation)
    }

    private fun <T> committed(connection: Connection, isolation: Int, operation: () -> T): T {
        connection.autoCommit = false
        connection.transactionIsolation = isolation
        return try {
            operation().also { connection.commit() }
        } catch (error: Throwable) {
            runCatching { connection.rollback() }
            throw error
        }
    }

    private fun <T> async(operation: () -> T): CompletionStage<T> = worker.submit(operation)
}
