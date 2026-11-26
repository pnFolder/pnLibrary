package org.pnfolder.pnlibrary.core.menu.data.local;

import org.pnfolder.pnlibrary.core.menu.data.PlanInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateStatusMessages;

/** The update facts of {@link LocalMenuData} that need more than one call. */
final class LocalUpdateData {
    private LocalUpdateData() {
    }

    static PlanInfo plan(PnLibrary library) {
        org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot current = library.getUpdates().getPlans().current().orElse(null);
        if (current == null) {
            return null;
        }
        List<String> blockers = new ArrayList<>();
        for (org.pnfolder.pnlibrary.api.updates.state.BlockedReason blocker : current.getBlockers()) {
            blockers.add(UpdateStatusMessages.blocker(blocker));
        }
        boolean hasChanges = current.getPlan() != null && !current.getPlan().getChanges().isEmpty();
        return new PlanInfo(current.getState(), hasChanges, blockers);
    }

    static CompletionStage<Boolean> checkAll(PnLibrary library, UpdateActor actor) {
        return yes(library.getUpdates().getPlans().check(actor));
    }

    /** Whatever the stage produces, the menu only needs to know that it finished. */
    static CompletionStage<Boolean> yes(CompletionStage<?> stage) {
        return stage.thenApply(value -> Boolean.TRUE);
    }
}
