package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryPage
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import java.sql.Connection
import java.sql.Timestamp

/** Builds and runs filtered, paged ledger history queries against [JdbcCurrencySchema]. */
internal class JdbcHistoryQuery(
    schema: JdbcCurrencySchema,
    private val decoder: JdbcTransactionDecoder,
) {
    private val transactionsTable = schema.transactionsTable

    /** Returns one page of [currency] history matching [query], newest first. */
    fun page(connection: Connection, currency: CurrencyKey, query: CurrencyHistoryQuery): CurrencyHistoryPage {
        val history = statement(currency, query)
        connection.prepareStatement(history.sql).use { statement ->
            (history.parameters + listOf(query.limit + 1, query.offset)).forEachIndexed { index, value ->
                statement.setObject(index + 1, value)
            }
            statement.executeQuery().use { results ->
                val values = mutableListOf<CurrencyTransaction>()
                while (results.next()) values += decoder.decode(results)
                return CurrencyHistoryPage(
                    items = values.take(query.limit),
                    offset = query.offset,
                    hasMore = values.size > query.limit,
                )
            }
        }
    }

    private fun statement(currency: CurrencyKey, query: CurrencyHistoryQuery): SqlStatement {
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

    private data class SqlStatement(
        val sql: String,
        val parameters: List<Any>,
    )
}
