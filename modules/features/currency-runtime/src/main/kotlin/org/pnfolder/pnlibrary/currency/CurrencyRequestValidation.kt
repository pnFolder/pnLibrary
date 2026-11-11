package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType

/** Shared validation at both the provider and persistence boundaries. */
internal object CurrencyRequestValidation {
    fun failure(descriptor: CurrencyDescriptor, request: CurrencyTransactionRequest): String? {
        val amountValid = descriptor.accepts(request.amount) && when (request.type) {
            CurrencyTransactionType.SET_BALANCE -> request.amount.signum() >= 0
            CurrencyTransactionType.RESET -> request.amount.signum() == 0
            else -> request.amount.signum() > 0
        }
        if (!amountValid) return "Invalid amount for ${descriptor.fractionDigits}-digit currency"

        return when (request.type) {
            CurrencyTransactionType.CREDIT -> if (request.target == null) "Credit requires a target account" else null
            CurrencyTransactionType.DEBIT -> if (request.source == null) "Debit requires a source account" else null
            CurrencyTransactionType.TRANSFER -> when {
                request.source == null || request.target == null -> "Transfer requires source and target accounts"
                request.source == request.target -> "Transfer source and target must differ"
                else -> null
            }
            CurrencyTransactionType.SET_BALANCE,
            CurrencyTransactionType.RESET,
            -> if (request.target == null) "Operation requires a target account" else null
        }
    }
}
