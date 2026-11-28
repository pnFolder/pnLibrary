package org.pnfolder.pnlibrary.currency.storage;

import java.util.Objects;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    private final Map<CurrencyTransaction, String> fragments = new IdentityHashMap<>();

    public CurrencyFileWriter(@NotNull Runnable onEncode) {
        this.onEncode = Objects.requireNonNull(onEncode, "onEncode");
    }

    void write(@NotNull Writer target, @NotNull CurrencyLedgerState state) throws IOException {
        JsonObject balances = new JsonObject();
        for (Map.Entry<String, java.math.BigDecimal> entry : state.getBalances().entrySet()) {
            balances.addProperty(entry.getKey(), entry.getValue().toPlainString());
        }
        target.write("{\n  \"balances\": ");
        target.write(deeper(gson.toJson(balances), "  "));
        target.write(",\n  \"transactions\": ");
        writeTransactions(target, state.getTransactions());
        if (!state.archivedReceipts().isEmpty()) {
            target.write(",\n  \"archivedReceipts\": ");
            writeTransactions(target, state.archivedReceipts());
        }
        target.write("\n}");
        forgetAbsent(state);
    }

    private void writeTransactions(Writer target, Iterable<CurrencyTransaction> transactions) throws IOException {
        target.write("[");
        boolean first = true;
        for (CurrencyTransaction transaction : transactions) {
            target.write(first ? "\n    " : ",\n    ");
            target.write(fragment(transaction));
            first = false;
        }
        target.write(first ? "]" : "\n  ]");
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

    /** Drops pieces of transactions no longer held in the history or the receipt archive. */
    private void forgetAbsent(CurrencyLedgerState state) {
        if (fragments.size() <= state.getTransactions().size() + state.archivedReceipts().size()) {
            return;
        }
        Set<CurrencyTransaction> present = Collections.newSetFromMap(new IdentityHashMap<>());
        present.addAll(state.getTransactions());
        present.addAll(state.archivedReceipts());
        fragments.keySet().retainAll(present);
    }
}
