package org.pnfolder.pnlibrary.currency.storage;

import java.util.Objects;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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

/** Maps a ledger row of {@link JdbcCurrencySchema} to a {@link CurrencyTransaction}. */
final class JdbcTransactionDecoder {
    private static final Type METADATA_TYPE = new TypeToken<Map<String, String>>() { }.getType();

    private final Gson gson;

    public JdbcTransactionDecoder(@NotNull Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    /** Reads the current row of {@code result}; the caller advances the cursor. */
    @NotNull
    CurrencyTransaction decode(@NotNull ResultSet result) throws SQLException {
        String[] parts = result.getString("currency_id").split(":", 2);
        Map<String, String> metadata = gson.fromJson(result.getString("metadata_text"), METADATA_TYPE);
        return new CurrencyTransaction(
                UUID.fromString(result.getString("transaction_id")),
                new CurrencyKey(PluginId.of(parts[0]), parts[1]),
                CurrencyTransactionType.valueOf(result.getString("transaction_type")),
                CurrencyTransactionStatus.valueOf(result.getString("status")),
                result.getBigDecimal("amount"),
                account(result, "source_account"),
                account(result, "target_account"),
                new CurrencyActor(CurrencyActorType.valueOf(result.getString("actor_type")), result.getString("actor_id")),
                result.getString("service_id"),
                result.getString("reason_text"),
                metadata,
                result.getBigDecimal("source_before"),
                result.getBigDecimal("source_after"),
                result.getBigDecimal("target_before"),
                result.getBigDecimal("target_after"),
                result.getTimestamp("created_at").toInstant(),
                result.getString("failure_text"),
                result.getString("idempotency_key"));
    }

    @Nullable
    private static CurrencyAccount account(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        return value == null ? null : new CurrencyAccount(UUID.fromString(value));
    }
}
