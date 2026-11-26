package org.pnfolder.pnlibrary.core.menu.data;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

/** State of the pending update plan and what blocks it. */
public final class PlanInfo {
    private final UpdateState state;
    private final boolean hasChanges;
    private final List<String> blockers;

    public PlanInfo(@NotNull UpdateState state, boolean hasChanges, @NotNull List<String> blockers) {
        this.state = state;
        this.hasChanges = hasChanges;
        this.blockers = blockers;
    }

    public @NotNull UpdateState getState() {
        return state;
    }

    public boolean hasChanges() {
        return hasChanges;
    }

    public @NotNull List<String> getBlockers() {
        return blockers;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PlanInfo)) {
            return false;
        }
        PlanInfo that = (PlanInfo) other;
        return state == that.state && hasChanges == that.hasChanges && blockers.equals(that.blockers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, hasChanges, blockers);
    }

    @Override
    public String toString() {
        return "PlanInfo(state=" + state + ", hasChanges=" + hasChanges + ", blockers=" + blockers + ")";
    }
}
