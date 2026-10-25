package org.pnfolder.pnlibrary.api.currency

import java.util.concurrent.CompletionStage

/** Balance mutation represented by a ledger transaction. */
enum class CurrencyTransactionType {
    /** Adds [CurrencyTransaction.amount] to the target account. */ CREDIT,
    /** Subtracts the amount from the source when sufficient funds exist. */ DEBIT,
    /** Atomically subtracts from the source and adds to the target. */ TRANSFER,
    /** Replaces the target balance with the amount. */ SET_BALANCE,
    /** Restores the target account balance to zero. */ RESET,
}

/** Kind of principal responsible for requesting a transaction. */
enum class CurrencyActorType {
    /** A player initiated the operation. */ PLAYER,
    /** A registered plugin initiated the operation. */ PLUGIN,
    /** A named application service initiated the operation. */ SERVICE,
    /** The current Minecraft server instance initiated the operation. */ SERVER,
    /** An internal or automated system process initiated the operation. */ SYSTEM,
}

/** Persisted outcome of one transaction request. */
enum class CurrencyTransactionStatus {
    /** Balance mutation and ledger append completed atomically. */ COMMITTED,
    /** Business validation refused the mutation, but the refusal was recorded. */ REJECTED,
    /** A failed request was recorded without committing its intended mutation. */ FAILED,
}

/** One offset-based page of reverse-chronological transaction history. */
data class CurrencyHistoryPage(
    /** Transactions in newest-first order. */
    val items: List<CurrencyTransaction>,
    /** Requested zero-based offset in the filtered result. */
    val offset: Int,
    /** Whether another item exists after this page. */
    val hasMore: Boolean,
)

/** Advanced managed-currency API available through `currency.extension(CurrencyLedger::class.java)`. */
interface CurrencyLedger {
    /** Applies one managed-currency transaction request. */
    fun transact(request: CurrencyTransactionRequest): CompletionStage<CurrencyTransaction>
    /** Queries the managed currency's persisted ledger. */
    fun history(query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage>
}
