package org.pnfolder.pnlibrary.api.audienceB2.delivery;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The answer of one receiver. */
public final class Receipt {
    private final String receiver;
    private final DeliveryStatus status;
    private final Throwable cause;

    /**
     * @param receiver its name, or {@code null} when the call did not go to a named receiver
     * @param status {@link DeliveryStatus#DELIVERED}, {@link DeliveryStatus#UNSUPPORTED} or {@link DeliveryStatus#FAILED}
     * @param cause the failure, only for {@link DeliveryStatus#FAILED}
     */
    public Receipt(@Nullable String receiver, @NotNull DeliveryStatus status, @Nullable Throwable cause) {
        this.receiver = receiver;
        this.status = Objects.requireNonNull(status, "status");
        this.cause = cause;
    }

    /** A receipt without a failure. */
    public Receipt(@Nullable String receiver, @NotNull DeliveryStatus status) {
        this(receiver, status, null);
    }

    /** The receiver's name, or {@code null} when the call did not go to a named receiver. */
    public @Nullable String getReceiver() {
        return receiver;
    }

    /** What became of this receiver. */
    public @NotNull DeliveryStatus getStatus() {
        return status;
    }

    /** The failure, only for {@link DeliveryStatus#FAILED}. */
    public @Nullable Throwable getCause() {
        return cause;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Receipt)) {
            return false;
        }
        Receipt that = (Receipt) other;
        return Objects.equals(receiver, that.receiver) && status == that.status && Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiver, status, cause);
    }

    @Override
    public String toString() {
        return "Receipt(receiver=" + receiver + ", status=" + status + ", cause=" + cause + ")";
    }
}
