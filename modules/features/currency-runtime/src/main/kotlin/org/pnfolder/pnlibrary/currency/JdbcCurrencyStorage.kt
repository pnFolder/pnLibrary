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
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.CompletionStage
import javax.sql.DataSource

/**
 * JDBC currency storage: each balance change and its ledger row commit in one SERIALIZABLE transaction.
 * Row SQL lives in [JdbcCurrencyLedger], history paging in [JdbcHistoryQuery], snapshots in [JdbcCurrencySnapshots].
 */
internal class JdbcCurrencyStorage(
    private val dataSource: DataSource,
    tablePrefix: String,
) : CurrencyStorage {
    private val schema = JdbcCurrencySchema(tablePrefix)
    private val schemaLock = Any()

    @Volatile
    private var initialized = false
    private val gson = Gson()
    private val decoder = JdbcTransactionDecoder(gson)
    private val ledger = JdbcCurrencyLedger(schema, gson, decoder)
    private val historyQuery = JdbcHistoryQuery(schema, decoder)
    private val snapshots = JdbcCurrencySnapshots(schema, ledger, decoder)
    private val worker = ManagedWorker("pnLibrary-currency-jdbc", parallelism = 2)

    override fun balance(currency: CurrencyKey, account: CurrencyAccount): CompletionStage<BigDecimal> = async {
        connection { connection -> ledger.readBalance(connection, currency, account) }
    }

    override fun transact(
        currency: CurrencyKey,
        descriptor: CurrencyDescriptor,
        request: CurrencyTransactionRequest,
    ): CompletionStage<CurrencyTransaction> = async {
        retryOnConflict {
            connection { connection ->
                committed(connection, Connection.TRANSACTION_SERIALIZABLE) {
                    request.idempotencyKey?.let { ledger.findIdempotent(connection, currency, it) }
                        ?: applyMutation(connection, currency, descriptor, request)
                }
            }
        }
    }

    private fun applyMutation(
        connection: Connection,
        currency: CurrencyKey,
        descriptor: CurrencyDescriptor,
        request: CurrencyTransactionRequest,
    ): CurrencyTransaction {
        // Rows are locked in one fixed order so two opposite transfers cannot deadlock each other.
        val balances = listOfNotNull(request.source, request.target)
            .distinctBy { it.playerId }
            .sortedBy { it.playerId.toString() }
            .associateWith { ledger.lockBalance(connection, currency, it) }
        val sourceBefore = request.source?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
        val targetBefore = request.target?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
        val mutation = CurrencyMutationEngine.evaluate(request, sourceBefore, targetBefore, descriptor)
        if (mutation.committed) {
            request.source?.let { ledger.writeBalance(connection, currency, it, descriptor.normalize(requireNotNull(mutation.sourceAfter))) }
            request.target?.let { ledger.writeBalance(connection, currency, it, descriptor.normalize(requireNotNull(mutation.targetAfter))) }
        }
        return mutation.transaction(currency, descriptor, request).also { ledger.insertTransaction(connection, it) }
    }

    /**
     * Retries [block] on a serialization failure or deadlock (PostgreSQL, MySQL) and on a constraint violation:
     * a concurrent request may have inserted the same new account row or idempotency key, and the next attempt
     * sees that row instead of colliding with it.
     */
    private fun <T> retryOnConflict(block: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (error: SQLException) {
                if (!isConflict(error) || attempt >= MAX_ATTEMPTS) throw error
                attempt++
            }
        }
    }

    private fun isConflict(error: SQLException): Boolean {
        val state = error.sqlState ?: return false
        return state == "40001" || state == "40P01" || state.startsWith("23")
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
        if (initialized) return
        synchronized(schemaLock) {
            if (initialized) return
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(schema.createAccountsTableSql())
                    statement.executeUpdate(schema.createTransactionsTableSql())
                }
            }
            initialized = true
        }
    }

    private fun <T> connection(operation: (Connection) -> T): T {
        ensureSchema()
        return dataSource.connection.use(operation)
    }

    private fun <T> committed(connection: Connection, isolation: Int, operation: () -> T): T {
        val previous = ConnectionSettings.capture(connection)
        connection.autoCommit = false
        connection.transactionIsolation = isolation
        return try {
            operation().also { connection.commit() }
        } catch (error: Throwable) {
            runCatchingNonFatal { connection.rollback() }
            throw error
        } finally {
            previous.restore(connection)
        }
    }

    private fun <T> async(operation: () -> T): CompletionStage<T> = worker.submit(operation)

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/** Transaction settings of a borrowed connection, put back before it returns to the caller's pool. */
private class ConnectionSettings(private val autoCommit: Boolean, private val isolation: Int) {
    fun restore(connection: Connection) {
        runCatchingNonFatal { connection.autoCommit = autoCommit }
        runCatchingNonFatal { connection.transactionIsolation = isolation }
    }

    companion object {
        fun capture(connection: Connection) = ConnectionSettings(connection.autoCommit, connection.transactionIsolation)
    }
}
