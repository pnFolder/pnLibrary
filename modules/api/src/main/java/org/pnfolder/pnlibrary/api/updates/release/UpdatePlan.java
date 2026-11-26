package org.pnfolder.pnlibrary.api.updates.release;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Immutable atomic update target. */
public final class UpdatePlan {
    private final int targetApi;
    private final List<ProductChange> changes;
    private final List<ProductRelease> selected;

    /**
     * Creates the plan.
     *
     * @param targetApi pnLibrary API generation selected for the complete graph
     * @param changes version transitions required to apply the plan
     * @param selected complete release set chosen by the resolver
     */
    public UpdatePlan(int targetApi, @NotNull List<ProductChange> changes, @NotNull List<ProductRelease> selected) {
        if (targetApi <= 0) {
            throw new IllegalArgumentException("Target API generation must be positive");
        }
        this.targetApi = targetApi;
        this.changes = changes;
        this.selected = selected;
    }

    /** pnLibrary API generation selected for the complete graph. */
    public int getTargetApi() {
        return targetApi;
    }

    /** Version transitions required to apply the plan. */
    @NotNull
    public List<ProductChange> getChanges() {
        return changes;
    }

    /** Complete release set chosen by the resolver. */
    @NotNull
    public List<ProductRelease> getSelected() {
        return selected;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UpdatePlan)) {
            return false;
        }
        UpdatePlan that = (UpdatePlan) other;
        return targetApi == that.targetApi && changes.equals(that.changes) && selected.equals(that.selected);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * targetApi + changes.hashCode()) + selected.hashCode();
    }

    @Override
    public String toString() {
        return "UpdatePlan(targetApi=" + targetApi + ", changes=" + changes + ", selected=" + selected + ")";
    }
}
