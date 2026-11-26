package org.pnfolder.pnlibrary.api.updates;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;

/** Looking for releases and turning them into a plan that is downloaded, verified and applied at restart. */
public interface UpdatePlans {
    /** Asks every catalogue for releases and resolves one plan that fits every registered product together. */
    @NotNull
    default CompletionStage<UpdatePlanSnapshot> check() {
        return check(UpdateActor.UNKNOWN);
    }

    /** Checks for releases as {@link #check()} does, recorded for {@code actor}. */
    @NotNull
    CompletionStage<UpdatePlanSnapshot> check(@NotNull UpdateActor actor);

    /** The newest plan, when one has been resolved. */
    @NotNull
    Optional<UpdatePlanSnapshot> current();

    /** The latest plans in memory, newest first, bounded. */
    @NotNull
    List<UpdatePlanSnapshot> recent();

    /** Downloads and verifies every file of the plan {@code planId}, so it can be applied at the next restart. */
    @NotNull
    default CompletionStage<UpdatePlanSnapshot> stage(@NotNull UUID planId) {
        return stage(planId, UpdateActor.UNKNOWN);
    }

    /** Stages a plan as {@link #stage(UUID)} does, recorded for {@code actor}. */
    @NotNull
    CompletionStage<UpdatePlanSnapshot> stage(@NotNull UUID planId, @NotNull UpdateActor actor);
}
