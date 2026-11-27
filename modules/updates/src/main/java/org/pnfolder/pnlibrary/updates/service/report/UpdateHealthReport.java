package org.pnfolder.pnlibrary.updates.service.report;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;

/** Observed loaded versions for one durable transaction, independent of the current release plan. */
public final class UpdateHealthReport {
    private final UUID transactionId;
    private final UUID planId;
    private final List<ProductChange> changes;
    private final boolean healthy;
    private final boolean rollback;
    private final String message;

    /** Creates a report of the versions a restart actually loaded. */
    public UpdateHealthReport(@NotNull UUID transactionId, @NotNull UUID planId, @NotNull List<ProductChange> changes,
                              boolean healthy, boolean rollback, @NotNull String message) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.planId = Objects.requireNonNull(planId, "planId");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.healthy = healthy;
        this.rollback = rollback;
        this.message = Objects.requireNonNull(message, "message");
    }

    /** The transaction the report is about. */
    @NotNull
    public UUID getTransactionId() {
        return transactionId;
    }

    /** The plan that started the transaction. */
    @NotNull
    public UUID getPlanId() {
        return planId;
    }

    /** The version moves the restart confirmed. */
    @NotNull
    public List<ProductChange> getChanges() {
        return changes;
    }

    /** Whether the loaded versions are the expected ones. */
    public boolean getHealthy() {
        return healthy;
    }

    /** Whether the transaction was a rollback. */
    public boolean getRollback() {
        return rollback;
    }

    /** What happened, for the administrator. */
    @NotNull
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UpdateHealthReport)) {
            return false;
        }
        UpdateHealthReport that = (UpdateHealthReport) other;
        return healthy == that.healthy && rollback == that.rollback && transactionId.equals(that.transactionId)
                && planId.equals(that.planId) && changes.equals(that.changes) && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, planId, changes, healthy, rollback, message);
    }

    @Override
    public String toString() {
        return "UpdateHealthReport(transactionId=" + transactionId + ", planId=" + planId + ", changes=" + changes
                + ", healthy=" + healthy + ", rollback=" + rollback + ", message=" + message + ")";
    }
}
