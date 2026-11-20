package org.pnfolder.pnlibrary.currency

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import java.io.Writer
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Writes the currency file. Every transaction is encoded once and its text is kept, so a save only joins ready pieces;
 * the bytes are exactly what Gson's pretty printer gives for the whole tree.
 */
internal class CurrencyFileWriter(private val onEncode: () -> Unit = {}) {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    // Identity, not equality: the ledger keeps the same instances between saves, and a different instance is simply encoded again.
    private val fragments = IdentityHashMap<CurrencyTransaction, String>()

    fun write(target: Writer, state: CurrencyLedgerState) {
        val balances = JsonObject().also { values -> state.balances.forEach { (key, value) -> values.addProperty(key, value.toPlainString()) } }
        if (state.transactions.isEmpty()) {
            fragments.clear()
            val root = JsonObject().also { it.add("balances", balances); it.add("transactions", JsonArray()) }
            gson.toJson(root, target)
            return
        }
        target.write("{\n  \"balances\": ")
        target.write(deeper(gson.toJson(balances), "  "))
        target.write(",\n  \"transactions\": [")
        state.transactions.forEachIndexed { index, transaction ->
            target.write(if (index == 0) "\n    " else ",\n    ")
            target.write(fragment(transaction))
        }
        target.write("\n  ]\n}")
        forgetAbsent(state)
    }

    private fun fragment(transaction: CurrencyTransaction): String = fragments.getOrPut(transaction) {
        onEncode()
        deeper(gson.toJson(CurrencyTransactionJson.encode(transaction)), "    ")
    }

    // String values never hold raw line breaks (Gson escapes them), so every line break is structure.
    private fun deeper(text: String, indent: String) = text.replace("\n", "\n$indent")

    /** Drops pieces of transactions that left the ledger: trimmed, replaced or rolled back. */
    private fun forgetAbsent(state: CurrencyLedgerState) {
        if (fragments.size <= state.transactions.size) return
        val present = Collections.newSetFromMap(IdentityHashMap<CurrencyTransaction, Boolean>())
        present.addAll(state.transactions)
        fragments.keys.retainAll(present)
    }
}
