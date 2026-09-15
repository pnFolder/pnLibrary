package ru.privatenull.pnlibrary.core.currency

import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionRequest
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionStatus
import ru.privatenull.pnlibrary.api.currency.CurrencyTransactionType
import java.math.BigDecimal

/** Pure balance transition shared by persistent currency backends. */
internal object CurrencyMutationEngine {
    /**
     * Evaluates one already validated request without performing persistence.
     *
     * A rejected transition preserves both balances. Storage implementations are
     * responsible for descriptor normalization before reading and after committing.
     */
    fun evaluate(
        request: CurrencyTransactionRequest,
        sourceBefore: BigDecimal?,
        targetBefore: BigDecimal?,
    ): CurrencyMutation {
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

    private fun rejected(source: BigDecimal?, target: BigDecimal?) = CurrencyMutation(
        status = CurrencyTransactionStatus.REJECTED,
        sourceBefore = source,
        sourceAfter = source,
        targetBefore = target,
        targetAfter = target,
        failure = "Insufficient funds",
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
)
