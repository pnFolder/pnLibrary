package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode
import org.pnfolder.pnlibrary.api.currency.CurrencyImportResult
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import java.math.BigDecimal
import java.sql.Connection
import java.time.Instant
import java.util.UUID

/** Reads and writes whole-currency snapshots inside a caller-managed JDBC transaction. */
internal class JdbcCurrencySnapshots(
    schema: JdbcCurrencySchema,
    private val ledger: JdbcCurrencyLedger,
    private val decoder: JdbcTransactionDecoder,
) {
    private val accountsTable = schema.accountsTable
    private val transactionsTable = schema.transactionsTable

    /** Reads every balance and ledger row of [currency]. */
    fun export(connection: Connection, currency: CurrencyKey): CurrencyStorageSnapshot {
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
        return CurrencyStorageSnapshot(
            currency = currency,
            createdAt = Instant.now(),
            balances = balances,
            transactions = transactions,
        )
    }

    /** Writes [snapshot] according to [mode] and counts imported and skipped rows. */
    fun import(connection: Connection, snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode): CurrencyImportResult {
        if (mode == CurrencyImportMode.REPLACE) {
            ledger.deleteCurrency(connection, snapshot.currency)
        }
        var importedAccounts = 0
        var skippedAccounts = 0
        snapshot.balances.forEach { (accountId, balance) ->
            val account = CurrencyAccount(accountId)
            val exists = ledger.accountExists(connection, snapshot.currency, account)
            if (exists && mode == CurrencyImportMode.MERGE_KEEP_TARGET) {
                skippedAccounts++
            } else {
                ledger.writeBalance(connection, snapshot.currency, account, balance)
                importedAccounts++
            }
        }
        var importedTransactions = 0
        var skippedTransactions = 0
        snapshot.transactions.forEach { transaction ->
            if (ledger.transactionExists(connection, transaction)) {
                skippedTransactions++
            } else {
                ledger.insertTransaction(connection, transaction)
                importedTransactions++
            }
        }
        return CurrencyImportResult(
            currency = snapshot.currency,
            mode = mode,
            importedAccounts = importedAccounts,
            skippedAccounts = skippedAccounts,
            importedTransactions = importedTransactions,
            skippedTransactions = skippedTransactions,
        )
    }
}
