package org.pnfolder.pnlibrary.api.audience.delivery;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The answer of one receiver. */
public final class Receipt {
    private final String receiver;
    private final Outcome outcome;
    private final Throwable cause;

    /**
     * @param receiver its name, or {@code null} when the platform call did not go to a named receiver
     * @param outcome what happened
     * @param cause the failure reported by the platform, only for {@link Outcome#FAILED}
     */
    public Receipt(@Nullable String receiver, @NotNull Outcome outcome, @Nullable Throwable cause) {
        this.receiver = receiver;
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.cause = cause;
    }

    /** A receipt without a failure. */
    public Receipt(@Nullable String receiver, @NotNull Outcome outcome) {
        this(receiver, outcome, null);
    }

    /** The receiver's name, or {@code null} when the platform call did not go to a named receiver. */
    public @Nullable String getReceiver() {
        return receiver;
    }

    /** What happened. */
    public @NotNull Outcome getOutcome() {
        return outcome;
    }

    /** The failure reported by the platform, only for {@link Outcome#FAILED}. */
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
        return Objects.equals(receiver, that.receiver) && outcome == that.outcome && Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiver, outcome, cause);
    }

    @Override
    public String toString() {
        return "Receipt(receiver=" + receiver + ", outcome=" + outcome + ", cause=" + cause + ")";
    }
}
