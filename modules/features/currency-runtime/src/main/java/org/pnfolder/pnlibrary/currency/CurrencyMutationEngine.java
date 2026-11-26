package org.pnfolder.pnlibrary.currency;

import java.math.BigDecimal;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus;

/** Pure balance transition shared by persistent currency backends. */
final class CurrencyMutationEngine {
    /** Failure text of a debit or transfer the source cannot cover; providers map it to a reject reason. */
    static final String INSUFFICIENT_FUNDS = "Insufficient funds";

    private CurrencyMutationEngine() {
    }

    /**
     * Computes the new balances for {@code request} from the current ones. A rejected transition preserves both balances.
     * Storage implementations are responsible for descriptor normalization before reading and after committing.
     */
    @NotNull
    static CurrencyMutation evaluate(@NotNull CurrencyTransactionRequest request, @Nullable BigDecimal sourceBefore,
                                     @Nullable BigDecimal targetBefore, @NotNull CurrencyDescriptor descriptor) {
        String failure = CurrencyRequestValidation.failure(descriptor, request);
        if (failure != null) {
            return rejected(sourceBefore, targetBefore, failure);
        }
        BigDecimal sourceAfter = sourceBefore;
        BigDecimal targetAfter = targetBefore;
        switch (request.getType()) {
            case CREDIT:
                targetAfter = Objects.requireNonNull(targetBefore).add(request.getAmount());
                break;
            case DEBIT:
                BigDecimal balance = Objects.requireNonNull(sourceBefore);
                if (balance.compareTo(request.getAmount()) < 0) {
                    return rejected(sourceBefore, targetBefore, INSUFFICIENT_FUNDS);
                }
                sourceAfter = balance.subtract(request.getAmount());
                break;
            case TRANSFER:
                BigDecimal sourceBalance = Objects.requireNonNull(sourceBefore);
                BigDecimal targetBalance = Objects.requireNonNull(targetBefore);
                if (sourceBalance.compareTo(request.getAmount()) < 0) {
                    return rejected(sourceBefore, targetBefore, INSUFFICIENT_FUNDS);
                }
                sourceAfter = sourceBalance.subtract(request.getAmount());
                targetAfter = targetBalance.add(request.getAmount());
                break;
            case SET_BALANCE:
                targetAfter = request.getAmount();
                break;
            default:
                targetAfter = BigDecimal.ZERO;
                break;
        }
        return new CurrencyMutation(CurrencyTransactionStatus.COMMITTED, sourceBefore, sourceAfter, targetBefore, targetAfter, null);
    }

    /** Creates a rejection that leaves both balances unchanged. */
    private static CurrencyMutation rejected(BigDecimal source, BigDecimal target, String failure) {
        return new CurrencyMutation(CurrencyTransactionStatus.REJECTED, source, source, target, target, failure);
    }
}
