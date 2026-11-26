package org.pnfolder.pnlibrary.currency.storage;

import org.pnfolder.pnlibrary.currency.storage.CurrencyLedgerState;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

/** Reads the currency file back into a {@link CurrencyLedgerState}. */
final class CurrencyFileLoader {
    private CurrencyFileLoader() {
    }

    /** Reads balances and ledger from {@code file}; a missing file means an empty store. */
    static void load(@NotNull Path file, @NotNull CurrencyLedgerState state) {
        if (!Files.exists(file)) {
            return;
        }
        if (Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("Currency storage file must not be a symbolic link");
        }
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        JsonObject balances = root.getAsJsonObject("balances");
        if (balances != null) {
            for (Map.Entry<String, JsonElement> entry : balances.entrySet()) {
                state.getBalances().put(entry.getKey(), new BigDecimal(entry.getValue().getAsString()));
            }
        }
        JsonArray transactions = root.getAsJsonArray("transactions");
        if (transactions != null) {
            for (JsonElement element : transactions) {
                state.record(CurrencyTransactionJson.decode(element.getAsJsonObject()));
            }
        }
    }
}
