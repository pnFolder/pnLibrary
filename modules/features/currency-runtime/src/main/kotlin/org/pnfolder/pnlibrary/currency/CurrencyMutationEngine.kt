package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Pure balance transition shared by persistent currency backends. */
internal object CurrencyMutationEngine {
    /** Failure text of a debit or transfer the source cannot cover; providers map it to a reject reason. */
    const val INSUFFICIENT_FUNDS = "Insufficient funds"

    /**
     * A rejected transition preserves both balances. Storage implementations are
     * responsible for descriptor normalization before reading and after committing.
     */
    fun evaluate(
        request: CurrencyTransactionRequest,
        sourceBefore: BigDecimal?,
        targetBefore: BigDecimal?,
        descriptor: CurrencyDescriptor,
    ): CurrencyMutation {
        CurrencyRequestValidation.failure(descriptor, request)?.let { failure ->
            return rejected(sourceBefore, targetBefore, failure)
        }
        var sourceAfter = sourceBefore
        var targetAfter = targetBefore

        when (request.type) {
            CurrencyTransactionType.CREDIT -> targetAfter = requireNotNull(targetBefore) + request.amount
            CurrencyTransactionType.DEBIT -> {
                val balance = requireNotNull(sourceBefore)
                if (balance < request.amount) return rejected(sourceBefore, targetBefore)
                sourceAfter = balance - request.amount
            }
            CurrencyTransactionType.TRANSFER -> {
                val sourceBalance = requireNotNull(sourceBefore)
                val targetBalance = requireNotNull(targetBefore)
                if (sourceBalance < request.amount) return rejected(sourceBefore, targetBefore)
                sourceAfter = sourceBalance - request.amount
                targetAfter = targetBalance + request.amount
            }
            CurrencyTransactionType.SET_BALANCE -> targetAfter = request.amount
            CurrencyTransactionType.RESET -> targetAfter = BigDecimal.ZERO
        }

        return CurrencyMutation(
            status = CurrencyTransactionStatus.COMMITTED,
            sourceBefore = sourceBefore,
            sourceAfter = sourceAfter,
            targetBefore = targetBefore,
            targetAfter = targetAfter,
        )
    }

    private fun rejected(source: BigDecimal?, target: BigDecimal?, failure: String = INSUFFICIENT_FUNDS) = CurrencyMutation(
        status = CurrencyTransactionStatus.REJECTED,
        sourceBefore = source,
        sourceAfter = source,
        targetBefore = target,
        targetAfter = target,
        failure = failure,
    )
}

/** Complete in-memory result of evaluating one balance mutation. */
internal data class CurrencyMutation(
    val status: CurrencyTransactionStatus,
    val sourceBefore: BigDecimal?,
    val sourceAfter: BigDecimal?,
    val targetBefore: BigDecimal?,
    val targetAfter: BigDecimal?,
    val failure: String? = null,
) {
    val committed: Boolean get() = status == CurrencyTransactionStatus.COMMITTED

    /** The ledger record of this mutation, created when the storage has applied it. */
    fun transaction(currency: CurrencyKey, descriptor: CurrencyDescriptor, request: CurrencyTransactionRequest) = CurrencyTransaction(
        id = UUID.randomUUID(),
        currency = currency,
        type = request.type,
        status = status,
        amount = if (committed) descriptor.normalize(request.amount) else request.amount,
        source = request.source,
        target = request.target,
        actor = request.actor,
        service = request.service,
        reason = request.reason,
        metadata = request.metadata,
        sourceBalanceBefore = sourceBefore,
        sourceBalanceAfter = sourceAfter,
        targetBalanceBefore = targetBefore,
        targetBalanceAfter = targetAfter,
        createdAt = Instant.now(),
        failure = failure,
        idempotencyKey = request.idempotencyKey,
    )
}
