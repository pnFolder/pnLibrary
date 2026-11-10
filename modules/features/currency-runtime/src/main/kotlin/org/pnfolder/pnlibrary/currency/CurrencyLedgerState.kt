package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import java.math.BigDecimal

/** Balances, ledger and idempotency index of the file storage; changes made with an undo log can be taken back step by step. */
internal class CurrencyLedgerState {
    val balances = linkedMapOf<String, BigDecimal>()
    val transactions = ArrayDeque<CurrencyTransaction>()
    private val idempotency = hashMapOf<String, CurrencyTransaction>()

    /** The earlier transaction recorded under the request's idempotency key, if any. */
    fun replay(currency: CurrencyKey, request: CurrencyTransactionRequest): CurrencyTransaction? =
        request.idempotencyKey?.let { idempotency[idempotencyKey(currency, it)] }

    fun hasIdempotencyKey(currency: CurrencyKey, key: String) = idempotencyKey(currency, key) in idempotency

    /** Evaluates [request] against current balances, stores the new balances when it commits, records and trims the ledger. */
    fun apply(
        currency: CurrencyKey,
        descriptor: CurrencyDescriptor,
        request: CurrencyTransactionRequest,
        maximumTransactions: Int,
        undo: LedgerUndo,
    ): CurrencyTransaction {
        val sourceKey = request.source?.let { accountKey(currency, it) }
        val targetKey = request.target?.let { accountKey(currency, it) }
        val sourceBefore = sourceKey?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
        val targetBefore = targetKey?.let { balances[it] ?: BigDecimal.ZERO }?.let(descriptor::normalize)
        val mutation = CurrencyMutationEngine.evaluate(request, sourceBefore, targetBefore, descriptor)
        if (mutation.committed) {
            sourceKey?.let { setBalance(it, descriptor.normalize(requireNotNull(mutation.sourceAfter)), undo) }
            targetKey?.let { setBalance(it, descriptor.normalize(requireNotNull(mutation.targetAfter)), undo) }
        }
        return mutation.transaction(currency, descriptor, request).also { transaction ->
            record(transaction, undo)
            trim(maximumTransactions, undo)
        }
    }

    /** Appends [transaction] to the ledger and indexes its idempotency key. */
    fun record(transaction: CurrencyTransaction, undo: LedgerUndo? = null) {
        transactions.addLast(transaction)
        val key = transaction.idempotencyKey?.let { idempotencyKey(transaction.currency, it) }
        val previous = key?.let { idempotency.put(it, transaction) }
        undo?.add {
            transactions.removeLast()
            if (key != null) if (previous == null) idempotency.remove(key) else idempotency[key] = previous
        }
    }

    /** Drops the oldest transactions beyond [maximumTransactions], together with their idempotency keys. */
    fun trim(maximumTransactions: Int, undo: LedgerUndo? = null) {
        while (transactions.size > maximumTransactions) {
            val old = transactions.removeFirst()
            val key = old.idempotencyKey?.let { idempotencyKey(old.currency, it) }
            val indexed = key != null && idempotency.remove(key, old)
            undo?.add {
                transactions.addFirst(old)
                if (indexed) idempotency[key!!] = old
            }
        }
    }

    /** Deletes all balances, transactions and idempotency keys of [currency]. */
    fun removeCurrency(currency: CurrencyKey) {
        val prefix = "$currency|"
        balances.keys.removeIf { it.startsWith(prefix) }
        transactions.removeAll { it.currency == currency }
        idempotency.keys.removeIf { it.startsWith(prefix) }
    }

    /** Full copy for operations that rewrite much of the state at once. */
    fun copy(): Copy = Copy(LinkedHashMap(balances), transactions.toList(), HashMap(idempotency))

    fun restore(copy: Copy) {
        balances.clear()
        balances.putAll(copy.balances)
        transactions.clear()
        transactions.addAll(copy.transactions)
        idempotency.clear()
        idempotency.putAll(copy.idempotency)
    }

    private fun setBalance(key: String, value: BigDecimal, undo: LedgerUndo) {
        val previous = balances.put(key, value)
        undo.add { if (previous == null) balances.remove(key) else balances[key] = previous }
    }

    class Copy(val balances: Map<String, BigDecimal>, val transactions: List<CurrencyTransaction>, val idempotency: Map<String, CurrencyTransaction>)

    companion object {
        /** Map key of one account balance: `currency|playerId`. */
        fun accountKey(currency: CurrencyKey, account: CurrencyAccount) = "$currency|${account.playerId}"

        /** Map key of one idempotency key, scoped per currency. */
        fun idempotencyKey(currency: CurrencyKey, key: String) = "$currency|$key"
    }
}

/** Reverse steps of the changes made to a [CurrencyLedgerState]; reverting restores it exactly. */
internal class LedgerUndo {
    private val steps = ArrayList<() -> Unit>()

    fun add(step: () -> Unit) {
        steps += step
    }

    /** Position to pass to [revertTo] to undo only what is added after this call. */
    fun mark(): Int = steps.size

    fun revertTo(mark: Int) {
        while (steps.size > mark) steps.removeAt(steps.size - 1)()
    }

    fun revert() = revertTo(0)
}
