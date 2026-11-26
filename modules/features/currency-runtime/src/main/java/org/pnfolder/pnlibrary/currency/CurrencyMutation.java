package org.pnfolder.pnlibrary.currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus;

/** Complete in-memory result of evaluating one balance mutation. */
final class CurrencyMutation {
    private final CurrencyTransactionStatus status;
    private final BigDecimal sourceBefore;
    private final BigDecimal sourceAfter;
    private final BigDecimal targetBefore;
    private final BigDecimal targetAfter;
    private final String failure;

    CurrencyMutation(@NotNull CurrencyTransactionStatus status, @Nullable BigDecimal sourceBefore,
                     @Nullable BigDecimal sourceAfter, @Nullable BigDecimal targetBefore,
                     @Nullable BigDecimal targetAfter, @Nullable String failure) {
        this.status = status;
        this.sourceBefore = sourceBefore;
        this.sourceAfter = sourceAfter;
        this.targetBefore = targetBefore;
        this.targetAfter = targetAfter;
        this.failure = failure;
    }

    @NotNull
    CurrencyTransactionStatus getStatus() {
        return status;
    }

    @Nullable
    BigDecimal getSourceBefore() {
        return sourceBefore;
    }

    @Nullable
    BigDecimal getSourceAfter() {
        return sourceAfter;
    }

    @Nullable
    BigDecimal getTargetBefore() {
        return targetBefore;
    }

    @Nullable
    BigDecimal getTargetAfter() {
        return targetAfter;
    }

    @Nullable
    String getFailure() {
        return failure;
    }

    /** True when the balances must be written. */
    boolean isCommitted() {
        return status == CurrencyTransactionStatus.COMMITTED;
    }

    /** The ledger record of this mutation, created when the storage has applied it. */
    @NotNull
    CurrencyTransaction transaction(@NotNull CurrencyKey currency, @NotNull CurrencyDescriptor descriptor,
                                    @NotNull CurrencyTransactionRequest request) {
        return new CurrencyTransaction(UUID.randomUUID(), currency, request.getType(), status,
                isCommitted() ? descriptor.normalize(request.getAmount()) : request.getAmount(),
                request.getSource(), request.getTarget(), request.getActor(), request.getService(), request.getReason(),
                request.getMetadata(), sourceBefore, sourceAfter, targetBefore, targetAfter, Instant.now(), failure,
                request.getIdempotencyKey());
    }
}
