package org.pnfolder.pnlibrary.currency

import com.google.gson.Gson
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Timestamp

/** Row-level balance and ledger statements of [JdbcCurrencySchema], run on a caller-owned connection. */
internal class JdbcCurrencyLedger(
    private val schema: JdbcCurrencySchema,
    private val gson: Gson,
    private val decoder: JdbcTransactionDecoder,
) {
    private val accountsTable = schema.accountsTable
    private val transactionsTable = schema.transactionsTable

    /** Returns the balance without locking; absent accounts read as zero. */
    fun readBalance(connection: Connection, currency: CurrencyKey, account: CurrencyAccount): BigDecimal =
        selectBalance(connection, currency, account, "")

    /** Reads the balance and locks its row until the transaction ends; SQLite has no row locks and needs none. */
    fun lockBalance(connection: Connection, currency: CurrencyKey, account: CurrencyAccount): BigDecimal {
        val sqlite = connection.metaData.databaseProductName.contains("SQLite", true)
        return selectBalance(connection, currency, account, if (sqlite) "" else " FOR UPDATE")
    }

    /** Selects the balance row, appending [suffix] (a lock clause or nothing). */
    private fun selectBalance(connection: Connection, currency: CurrencyKey, account: CurrencyAccount, suffix: String): BigDecimal {
        val sql = "SELECT balance FROM $accountsTable WHERE currency_id = ? AND account_id = ?$suffix"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, currency.toString())
            statement.setString(2, account.playerId.toString())
            statement.executeQuery().use { return if (it.next()) it.getBigDecimal(1) else BigDecimal.ZERO }
        }
    }

    /**
     * Updates the balance row of [account] or inserts it. A concurrent insert of the same row fails with a
     * constraint violation that [JdbcCurrencyStorage] retries; recovering here is impossible on PostgreSQL,
     * where any failed statement aborts the whole transaction.
     */
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
        connection.prepareStatement(
            "INSERT INTO $accountsTable (currency_id, account_id, balance) VALUES (?, ?, ?)",
        ).use { statement ->
            statement.setString(1, currency.toString())
            statement.setString(2, account.playerId.toString())
            statement.setBigDecimal(3, balance)
            statement.executeUpdate()
        }
    }

    /** Returns whether a balance row exists for [account]. */
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

    /** Inserts one ledger row. */
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

    /** Returns the transaction already stored under [key], or null when the key is new. */
    fun findIdempotent(connection: Connection, currency: CurrencyKey, key: String): CurrencyTransaction? {
        connection.prepareStatement(
            "SELECT * FROM $transactionsTable WHERE currency_id = ? AND idempotency_key = ?",
        ).use { statement ->
            statement.setString(1, currency.toString())
            statement.setString(2, key)
            statement.executeQuery().use { return if (it.next()) decoder.decode(it) else null }
        }
    }

    /** Deletes all balances and ledger rows of [currency]. */
    fun deleteCurrency(connection: Connection, currency: CurrencyKey) {
        listOf(transactionsTable, accountsTable).forEach { table ->
            connection.prepareStatement("DELETE FROM $table WHERE currency_id = ?").use { statement ->
                statement.setString(1, currency.toString())
                statement.executeUpdate()
            }
        }
    }
}
