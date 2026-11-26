package org.pnfolder.pnlibrary.currency.storage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyActorType;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** How a transaction is written to and read from the currency file. */
public final class CurrencyTransactionJson {
    private CurrencyTransactionJson() {
    }

    /** Serializes {@code value}; absent optional fields are omitted. */
    @NotNull
    public static JsonObject encode(@NotNull CurrencyTransaction value) {
        JsonObject json = new JsonObject();
        json.addProperty("id", value.getId().toString());
        json.addProperty("currency", value.getCurrency().toString());
        json.addProperty("type", value.getType().name());
        json.addProperty("status", value.getStatus().name());
        json.addProperty("amount", value.getAmount().toPlainString());
        if (value.getSource() != null) {
            json.addProperty("source", value.getSource().getPlayerId().toString());
        }
        if (value.getTarget() != null) {
            json.addProperty("target", value.getTarget().getPlayerId().toString());
        }
        json.addProperty("actorType", value.getActor().getType().name());
        json.addProperty("actorId", value.getActor().getId());
        json.addProperty("service", value.getService());
        if (value.getReason() != null) {
            json.addProperty("reason", value.getReason());
        }
        if (value.getFailure() != null) {
            json.addProperty("failure", value.getFailure());
        }
        if (value.getIdempotencyKey() != null) {
            json.addProperty("idempotencyKey", value.getIdempotencyKey());
        }
        json.addProperty("createdAt", value.getCreatedAt().toString());
        addDecimal(json, "sourceBalanceBefore", value.getSourceBalanceBefore());
        addDecimal(json, "sourceBalanceAfter", value.getSourceBalanceAfter());
        addDecimal(json, "targetBalanceBefore", value.getTargetBalanceBefore());
        addDecimal(json, "targetBalanceAfter", value.getTargetBalanceAfter());
        JsonObject metadata = new JsonObject();
        for (Map.Entry<String, String> entry : value.getMetadata().entrySet()) {
            metadata.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("metadata", metadata);
        return json;
    }

    private static void addDecimal(JsonObject json, String name, BigDecimal value) {
        if (value != null) {
            json.addProperty(name, value.toPlainString());
        }
    }

    /** Reads a transaction written by {@link #encode}; optional fields default to {@code null}. */
    @NotNull
    public static CurrencyTransaction decode(@NotNull JsonObject value) {
        String[] currencyParts = value.get("currency").getAsString().split(":", 2);
        Map<String, String> metadata = new LinkedHashMap<>();
        JsonObject stored = value.getAsJsonObject("metadata");
        if (stored != null) {
            for (Map.Entry<String, JsonElement> entry : stored.entrySet()) {
                metadata.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        JsonElement reason = value.get("reason");
        JsonElement failure = value.get("failure");
        JsonElement idempotencyKey = value.get("idempotencyKey");
        return new CurrencyTransaction(
                UUID.fromString(value.get("id").getAsString()), new CurrencyKey(PluginId.of(currencyParts[0]), currencyParts[1]),
                CurrencyTransactionType.valueOf(value.get("type").getAsString()),
                CurrencyTransactionStatus.valueOf(value.get("status").getAsString()),
                new BigDecimal(value.get("amount").getAsString()), account(value, "source"), account(value, "target"),
                new CurrencyActor(CurrencyActorType.valueOf(value.get("actorType").getAsString()), value.get("actorId").getAsString()),
                value.get("service").getAsString(), reason == null ? null : reason.getAsString(), metadata,
                decimal(value, "sourceBalanceBefore"), decimal(value, "sourceBalanceAfter"),
                decimal(value, "targetBalanceBefore"), decimal(value, "targetBalanceAfter"),
                Instant.parse(value.get("createdAt").getAsString()), failure == null ? null : failure.getAsString(),
                idempotencyKey == null ? null : idempotencyKey.getAsString());
    }

    @Nullable
    private static BigDecimal decimal(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? null : new BigDecimal(element.getAsString());
    }

    @Nullable
    private static CurrencyAccount account(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? null : new CurrencyAccount(UUID.fromString(element.getAsString()));
    }
}
