package org.pnfolder.pnlibrary.currency

import com.google.gson.Gson
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp

/** Row-level balance and ledger statements of [JdbcCurrencySchema], run on a caller-owned connection. */
internal class JdbcCurrencyLedger(
    private val schema: JdbcCurrencySchema,
    private val gson: Gson,
    private val decoder: JdbcTransactionDecoder,
) {
    private val accountsTable = schema.accountsTable
    private val transactionsTable = schema.transactionsTable

    /** Reads the balance of [account], optionally locking its row where the database supports it. */
    fun readBalance(
        connection: Connection,
        currency: CurrencyKey,
        account: CurrencyAccount,
        lock: Boolean,
    ): BigDecimal {
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

    /** Upserts the balance of [account], tolerating a concurrent insert of the same row. */
    fun writeBalance(
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

    /** Returns whether [account] has a stored balance row for [currency]. */
    fun accountExists(connection: Connection, currency: CurrencyKey, account: CurrencyAccount): Boolean {
        connection.prepareStatement(
            "SELECT 1 FROM $accountsTable WHERE currency_id = ? AND account_id = ?",
        ).use { statement ->
            statement.setString(1, currency.toString())
            statement.setString(2, account.playerId.toString())
            statement.executeQuery().use { return it.next() }
        }
    }

    /** Returns whether [transaction] is already recorded by ID or idempotency key. */
    fun transactionExists(connection: Connection, transaction: CurrencyTransaction): Boolean {
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

    /** Appends [value] to the ledger. */
    fun insertTransaction(connection: Connection, value: CurrencyTransaction) {
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

    /** Returns the transaction previously recorded under the idempotency [key], if any. */
    fun findIdempotent(connection: Connection, currency: CurrencyKey, key: String): CurrencyTransaction? {
        connection.prepareStatement(
            "SELECT * FROM $transactionsTable WHERE currency_id = ? AND idempotency_key = ?",
        ).use { statement ->
            statement.setString(1, currency.toString())
            statement.setString(2, key)
            statement.executeQuery().use { return if (it.next()) decoder.decode(it) else null }
        }
    }

    /** Deletes every balance and ledger row of [currency]. */
    fun deleteCurrency(connection: Connection, currency: CurrencyKey) {
        listOf(transactionsTable, accountsTable).forEach { table ->
            connection.prepareStatement("DELETE FROM $table WHERE currency_id = ?").use { statement ->
                statement.setString(1, currency.toString())
                statement.executeUpdate()
            }
        }
    }
}
