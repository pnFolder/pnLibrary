package org.pnfolder.pnlibrary.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;

/** Shared validation at both the provider and persistence boundaries. */
final class CurrencyRequestValidation {
    private CurrencyRequestValidation() {
    }

    /** Returns the reason {@code request} is invalid for {@code descriptor}, or {@code null} when it may proceed. */
    @Nullable
    static String failure(@NotNull CurrencyDescriptor descriptor, @NotNull CurrencyTransactionRequest request) {
        if (!amountValid(descriptor, request)) {
            return "Invalid amount for " + descriptor.getFractionDigits() + "-digit currency";
        }
        switch (request.getType()) {
            case CREDIT:
                return request.getTarget() == null ? "Credit requires a target account" : null;
            case DEBIT:
                return request.getSource() == null ? "Debit requires a source account" : null;
            case TRANSFER:
                if (request.getSource() == null || request.getTarget() == null) {
                    return "Transfer requires source and target accounts";
                }
                return request.getSource().equals(request.getTarget()) ? "Transfer source and target must differ" : null;
            default:
                return request.getTarget() == null ? "Operation requires a target account" : null;
        }
    }

    private static boolean amountValid(CurrencyDescriptor descriptor, CurrencyTransactionRequest request) {
        if (!descriptor.accepts(request.getAmount())) {
            return false;
        }
        int sign = request.getAmount().signum();
        switch (request.getType()) {
            case SET_BALANCE:
                return sign >= 0;
            case RESET:
                return sign == 0;
            default:
                return sign > 0;
        }
    }
}
