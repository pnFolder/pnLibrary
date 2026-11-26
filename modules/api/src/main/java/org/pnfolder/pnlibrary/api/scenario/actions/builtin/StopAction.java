package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

import java.util.Objects;

/** Ends the scenario here, quietly and without undoing anything. */
public final class StopAction implements Action {
    private final String reason;

    /** Creates the action with every setting at its default. */
    public StopAction() {
        this(null);
    }

    /** Creates it with every setting given. */
    public StopAction(@Nullable String reason) {
        this.reason = reason;
    }

    /** Optional note for the result. */
    @Nullable
    public String getReason() {
        return reason;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        return new ActionResult.Stopped(reason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StopAction)) {
            return false;
        }
        StopAction that = (StopAction) other;
        return Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason);
    }

    @NotNull
    @Override
    public String toString() {
        return "StopAction(reason=" + reason + ")";
    }
}
