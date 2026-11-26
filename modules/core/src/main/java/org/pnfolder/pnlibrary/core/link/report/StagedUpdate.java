package org.pnfolder.pnlibrary.core.link.report;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

/** Tells whether a prepared update is waiting for a restart, which is the one fact the restart features share. */
public final class StagedUpdate {
    private StagedUpdate() {
    }

    /** Whether the current plan is staged, so the next restart of this server or proxy applies it. */
    public static boolean waiting(@NotNull PnLibrary library) {
        return library.getUpdates().getPlans().current()
            .map(UpdatePlanSnapshot::getState)
            .filter(state -> state == UpdateState.UPDATE_STAGED)
            .isPresent();
    }
}
