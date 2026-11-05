package org.pnfolder.pnlibrary.currency

import com.google.gson.JsonObject
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyActorType
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.time.Instant
import java.util.UUID

/** How a transaction is written to and read from the currency file. */
internal object CurrencyTransactionJson {
    fun encode(value: CurrencyTransaction) = JsonObject().apply {
        addProperty("id", value.id.toString())
        addProperty("currency", value.currency.toString())
        addProperty("type", value.type.name)
        addProperty("status", value.status.name)
        addProperty("amount", value.amount.toPlainString())
        value.source?.let { addProperty("source", it.playerId.toString()) }
        value.target?.let { addProperty("target", it.playerId.toString()) }
        addProperty("actorType", value.actor.type.name)
        addProperty("actorId", value.actor.id)
        addProperty("service", value.service)
        value.reason?.let { addProperty("reason", it) }
        value.failure?.let { addProperty("failure", it) }
        value.idempotencyKey?.let { addProperty("idempotencyKey", it) }
        addProperty("createdAt", value.createdAt.toString())
        value.sourceBalanceBefore?.let { addProperty("sourceBalanceBefore", it.toPlainString()) }
        value.sourceBalanceAfter?.let { addProperty("sourceBalanceAfter", it.toPlainString()) }
        value.targetBalanceBefore?.let { addProperty("targetBalanceBefore", it.toPlainString()) }
        value.targetBalanceAfter?.let { addProperty("targetBalanceAfter", it.toPlainString()) }
        add("metadata", JsonObject().apply {
            value.metadata.forEach { (key, entry) -> addProperty(key, entry) }
        })
    }

    fun decode(value: JsonObject): CurrencyTransaction {
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
}
