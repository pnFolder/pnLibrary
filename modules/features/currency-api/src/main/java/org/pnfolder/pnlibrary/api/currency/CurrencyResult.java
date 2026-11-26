package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Objects;

/** Structured result returned by currency mutation capabilities. */
public final class CurrencyResult {
    private final CurrencyResultStatus status;
    private final BigDecimal previousBalance;
    private final BigDecimal currentBalance;
    private final CurrencyRejectReason reason;
    private final String message;
    private final Throwable error;

    /** Creates a result with only a status. */
    public CurrencyResult(@NotNull CurrencyResultStatus status) {
        this(status, null, null, null, null, null);
    }

    /** Creates a result; every argument except {@code status} may be {@code null}. */
    public CurrencyResult(@NotNull CurrencyResultStatus status, @Nullable BigDecimal previousBalance,
                          @Nullable BigDecimal currentBalance, @Nullable CurrencyRejectReason reason,
                          @Nullable String message, @Nullable Throwable error) {
        this.status = status;
        this.previousBalance = previousBalance;
        this.currentBalance = currentBalance;
        this.reason = reason;
        this.message = message;
        this.error = error;
    }

    /** High-level outcome used for program flow. */
    @NotNull
    public CurrencyResultStatus getStatus() {
        return status;
    }

    /** Balance before a committed operation, when available. */
    @Nullable
    public BigDecimal getPreviousBalance() {
        return previousBalance;
    }

    /** Balance after a committed operation, when available. */
    @Nullable
    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    /** Stable reason for a rejected operation. */
    @Nullable
    public CurrencyRejectReason getReason() {
        return reason;
    }

    /** Optional diagnostic or user-displayable detail supplied by the provider. */
    @Nullable
    public String getMessage() {
        return message;
    }

    /** Original unexpected failure; normally present only for {@link CurrencyResultStatus#FAILED}. */
    @Nullable
    public Throwable getError() {
        return error;
    }

    /** Whether the requested mutation committed successfully. */
    public boolean isSuccess() {
        return status == CurrencyResultStatus.SUCCESS;
    }

    /** Creates a successful result without balances. */
    @NotNull
    public static CurrencyResult success() {
        return success(null, null);
    }

    /** Creates a successful result with optional before-and-after balances. */
    @NotNull
    public static CurrencyResult success(@Nullable BigDecimal previous, @Nullable BigDecimal current) {
        return new CurrencyResult(CurrencyResultStatus.SUCCESS, previous, current, null, null, null);
    }

    /** Creates a business-level rejection that should not normally be retried unchanged. */
    @NotNull
    public static CurrencyResult rejected(@NotNull CurrencyRejectReason reason) {
        return rejected(reason, null);
    }

    /** Creates a business-level rejection with a message. */
    @NotNull
    public static CurrencyResult rejected(@NotNull CurrencyRejectReason reason, @Nullable String message) {
        return new CurrencyResult(CurrencyResultStatus.REJECTED, null, null, reason, message, null);
    }

    /** Creates a result for a capability the selected provider does not implement. */
    @NotNull
    public static CurrencyResult unsupported(@NotNull String message) {
        return new CurrencyResult(CurrencyResultStatus.UNSUPPORTED, null, null, null, message, null);
    }

    /** Creates a potentially retryable temporary-availability result. */
    @NotNull
    public static CurrencyResult unavailable(@NotNull String message) {
        return new CurrencyResult(CurrencyResultStatus.UNAVAILABLE, null, null, null, message, null);
    }

    /** Creates an unexpected-failure result while preserving the original {@code error}. */
    @NotNull
    public static CurrencyResult failed(@NotNull Throwable error) {
        return new CurrencyResult(CurrencyResultStatus.FAILED, null, null, null, error.getMessage(), error);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyResult)) {
            return false;
        }
        CurrencyResult that = (CurrencyResult) other;
        return status == that.status && Objects.equals(previousBalance, that.previousBalance)
                && Objects.equals(currentBalance, that.currentBalance) && reason == that.reason
                && Objects.equals(message, that.message) && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, previousBalance, currentBalance, reason, message, error);
    }

    @Override
    public String toString() {
        return "CurrencyResult(status=" + status + ", previousBalance=" + previousBalance + ", currentBalance="
                + currentBalance + ", reason=" + reason + ", message=" + message + ", error=" + error + ")";
    }
}
