package org.pnfolder.pnlibrary.currency;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;

/**
 * Writes the currency file. Every transaction is encoded once and its text is kept, so a save only joins ready pieces;
 * the bytes are exactly what Gson's pretty printer gives for the whole tree.
 */
final class CurrencyFileWriter {
    private final Runnable onEncode;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    // Identity, not equality: the ledger keeps the same instances between saves, and a different instance is simply encoded again.
    private final Map<CurrencyTransaction, String> fragments = new IdentityHashMap<CurrencyTransaction, String>();

    CurrencyFileWriter(@NotNull Runnable onEncode) {
        this.onEncode = onEncode;
    }

    void write(@NotNull Writer target, @NotNull CurrencyLedgerState state) throws IOException {
        JsonObject balances = new JsonObject();
        for (Map.Entry<String, java.math.BigDecimal> entry : state.getBalances().entrySet()) {
            balances.addProperty(entry.getKey(), entry.getValue().toPlainString());
        }
        if (state.getTransactions().isEmpty()) {
            fragments.clear();
            JsonObject root = new JsonObject();
            root.add("balances", balances);
            root.add("transactions", new JsonArray());
            gson.toJson(root, target);
            return;
        }
        target.write("{\n  \"balances\": ");
        target.write(deeper(gson.toJson(balances), "  "));
        target.write(",\n  \"transactions\": [");
        int index = 0;
        for (CurrencyTransaction transaction : state.getTransactions()) {
            target.write(index == 0 ? "\n    " : ",\n    ");
            target.write(fragment(transaction));
            index++;
        }
        target.write("\n  ]\n}");
        forgetAbsent(state);
    }

    private String fragment(CurrencyTransaction transaction) {
        String known = fragments.get(transaction);
        if (known != null) {
            return known;
        }
        onEncode.run();
        String encoded = deeper(gson.toJson(CurrencyTransactionJson.encode(transaction)), "    ");
        fragments.put(transaction, encoded);
        return encoded;
    }

    // String values never hold raw line breaks (Gson escapes them), so every line break is structure.
    private static String deeper(String text, String indent) {
        return text.replace("\n", "\n" + indent);
    }

    /** Drops pieces of transactions that left the ledger: trimmed, replaced or rolled back. */
    private void forgetAbsent(CurrencyLedgerState state) {
        if (fragments.size() <= state.getTransactions().size()) {
            return;
        }
        Set<CurrencyTransaction> present = Collections.newSetFromMap(new IdentityHashMap<CurrencyTransaction, Boolean>());
        present.addAll(state.getTransactions());
        fragments.keySet().retainAll(present);
    }
}
