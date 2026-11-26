package org.pnfolder.pnlibrary.updates.service;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;

/** The operations that change what is prepared on disk: staging a plan and rolling back to a saved set. */
final class PlanChanges {
    private final UpdateConfiguration configuration;
    private final UpdateOrchestratorHooks hooks;
    private final PlanState state;
    private final BooleanSupplier closed;

    PlanChanges(UpdateConfiguration configuration, UpdateOrchestratorHooks hooks, PlanState state, BooleanSupplier closed) {
        this.configuration = configuration;
        this.hooks = hooks;
        this.state = state;
        this.closed = closed;
    }

    /** Stages the freshly checked plan when the policy allows it without an administrator. */
    UpdatePlanSnapshot stageIfAutomatic(UpdatePlanSnapshot snapshot) throws IOException {
        boolean shouldStage = (configuration.getEffectiveAutomaticDownloads() || hooks.getAutomaticOverridesActive().getAsBoolean())
                && snapshot.getState() == UpdateState.UPDATE_AVAILABLE && hooks.getAutomaticAllowed().test(snapshot);
        if (!shouldStage) {
            return snapshot;
        }
        try {
            stage(snapshot, UpdateActor.SYSTEM);
        } catch (Throwable error) {
            state.observeFailure(snapshot.getId(), UpdateActor.SYSTEM, "STAGE", error);
            throw error;
        }
        return state.current();
    }

    /** Stages the plan with {@code planId} if it is still the current, available plan. */
    void stageRequested(CompletableFuture<UpdatePlanSnapshot> promise, UUID planId, UpdateActor actor) {
        if (promise.isDone()) {
            return;
        }
        try {
            UpdatePlanSnapshot selected = state.current();
            if (selected == null || !selected.getId().equals(planId)) {
                throw new IllegalArgumentException("Stale or unknown update plan: " + planId);
            }
            if (selected.getState() != UpdateState.UPDATE_AVAILABLE) {
                throw new IllegalArgumentException("Update plan is not available for staging");
            }
            stage(selected, actor);
            promise.complete(state.current());
        } catch (Throwable error) {
            state.observeFailure(planId, actor, "STAGE", error);
            promise.completeExceptionally(error);
        }
    }

    /** Runs {@code action} as a rollback and publishes the plan it returns. */
    void rollback(CompletableFuture<UpdatePlanSnapshot> promise, UpdateActor actor, @Nullable UUID failureId,
                  Callable<UpdatePlanSnapshot> action) {
        if (promise.isDone()) {
            return;
        }
        try {
            UpdatePlanSnapshot selected = action.call();
            UpdatePlanSnapshot restored = new UpdatePlanSnapshot(selected.getId(), state.nextRevision(),
                    UpdateState.ROLLED_BACK, selected.getPlan(), selected.getBlockers(), selected.getMessage());
            state.publish(restored, actor, "ROLLBACK");
            promise.complete(restored);
        } catch (Throwable error) {
            state.observeFailure(failureId != null ? failureId : state.currentIdOrRandom(), actor, "ROLLBACK", error);
            promise.completeExceptionally(error);
        }
    }

    private void stage(UpdatePlanSnapshot snapshot, UpdateActor actor) throws IOException {
        if (closed.getAsBoolean()) {
            throw new IllegalStateException("Update orchestrator is closed");
        }
        hooks.getStageAction().accept(snapshot);
        state.publish(new UpdatePlanSnapshot(snapshot.getId(), snapshot.getRevision(), UpdateState.UPDATE_STAGED,
                snapshot.getPlan(), snapshot.getBlockers(), null), actor, "STAGE");
    }
}
