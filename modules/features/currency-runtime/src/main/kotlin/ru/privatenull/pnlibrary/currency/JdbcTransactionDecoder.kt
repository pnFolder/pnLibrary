package ru.privatenull.pnlibrary.currency

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.privatenull.pnlibrary.api.currency.CurrencyAccount
import ru.privatenull.pnlibrary.api.currency.CurrencyActor
import ru.privatenull.pnlibrary.api.currency.CurrencyActorType
import ru.privatenull.pnlibrary.api.currency.CurrencyKey
import ru.privatenull.pnlibrary.api.currency.CurrencyTransaction
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionStatus
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionType
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.sql.ResultSet
import java.util.UUID

/** Maps a ledger row of [JdbcCurrencySchema] to a [CurrencyTransaction]. */
internal class JdbcTransactionDecoder(private val gson: Gson) {
    fun decode(result: ResultSet): CurrencyTransaction {
        val parts = result.getString("currency_id").split(':', limit = 2)
        fun account(column: String) = result.getString(column)?.let(UUID::fromString)?.let(::CurrencyAccount)
        val metadataType = object : TypeToken<Map<String, String>>() {}.type
        return CurrencyTransaction(
            id = UUID.fromString(result.getString("transaction_id")),
            currency = CurrencyKey(PluginId.of(parts[0]), parts[1]),
            type = CurrencyTransactionType.valueOf(result.getString("transaction_type")),
            status = CurrencyTransactionStatus.valueOf(result.getString("status")),
            amount = result.getBigDecimal("amount"),
            source = account("source_account"),
            target = account("target_account"),
            actor = CurrencyActor(
                CurrencyActorType.valueOf(result.getString("actor_type")),
                result.getString("actor_id"),
            ),
            service = result.getString("service_id"),
            reason = result.getString("reason_text"),
            metadata = gson.fromJson(result.getString("metadata_text"), metadataType),
            sourceBalanceBefore = result.getBigDecimal("source_before"),
            sourceBalanceAfter = result.getBigDecimal("source_after"),
            targetBalanceBefore = result.getBigDecimal("target_before"),
            targetBalanceAfter = result.getBigDecimal("target_after"),
            createdAt = result.getTimestamp("created_at").toInstant(),
            failure = result.getString("failure_text"),
            idempotencyKey = result.getString("idempotency_key"),
        )
    }
}
