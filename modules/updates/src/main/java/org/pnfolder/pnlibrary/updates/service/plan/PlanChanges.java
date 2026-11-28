package org.pnfolder.pnlibrary.updates.service.plan;

import org.pnfolder.pnlibrary.updates.service.orchestrator.UpdateOrchestratorHooks;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;

/** The operations that change what is prepared on disk: staging a plan and rolling back to a saved set. */
public final class PlanChanges {
    private final UpdateConfiguration configuration;
    private final UpdateOrchestratorHooks hooks;
    private final PlanState state;
    private final BooleanSupplier closed;

    public PlanChanges(UpdateConfiguration configuration, UpdateOrchestratorHooks hooks, PlanState state, BooleanSupplier closed) {
        this.configuration = configuration;
        this.hooks = hooks;
        this.state = state;
        this.closed = closed;
    }

    /** Stages the freshly checked plan when the policy allows it without an administrator. */
    UpdatePlanSnapshot stageIfAutomatic(UpdatePlanSnapshot snapshot) throws IOException {
        boolean shouldStage = snapshot.getState() == UpdateState.UPDATE_AVAILABLE && hooks.getAutomaticAllowed().test(snapshot);
        if (!shouldStage) {
            return snapshot;
        }
        try {
            stage(snapshot, UpdateActor.SYSTEM, "STAGE");
        } catch (Throwable error) {
            state.observeFailure(snapshot.getId(), UpdateActor.SYSTEM, "STAGE", error);
            throw error;
        }
        return state.current();
    }

    /** Stages the plan with {@code planId} if it is still the current, available plan. */
    public void stageRequested(CompletableFuture<UpdatePlanSnapshot> promise, UUID planId, UpdateActor actor) {
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
            stage(selected, actor, "STAGE");
            promise.complete(state.current());
        } catch (Throwable error) {
            state.observeFailure(planId, actor, "STAGE", error);
            promise.completeExceptionally(error);
        }
    }

    /** Resolves a dedicated plan and uses the same installation transaction as ordinary staging. */
    public void stageLibraryRequested(CompletableFuture<UpdatePlanSnapshot> promise, Supplier<UpdatePlan> resolve,
                                      UpdateActor actor) {
        if (promise.isDone()) {
            return;
        }
        UpdatePlanSnapshot staged = state.forcedStage();
        if (staged != null) {
            promise.complete(staged);
            return;
        }
        UUID id = UUID.randomUUID();
        try {
            UpdatePlan plan = resolve.get();
            if (plan.getChanges().size() != 1
                    || !PnLibraryIdentity.PRODUCT_ID.equals(plan.getChanges().get(0).getProduct().getValue())) {
                throw new IllegalArgumentException("A forced plan must update pnLibrary alone");
            }
            UpdatePlanSnapshot selected = new UpdatePlanSnapshot(id, state.nextRevision(), UpdateState.UPDATE_AVAILABLE,
                    plan, Collections.emptyList(), "Принудительное обновление pnLibrary.");
            stage(selected, actor, PlanState.FORCE_STAGE);
            promise.complete(state.current());
        } catch (Throwable error) {
            state.observeFailure(id, actor, PlanState.FORCE_STAGE, error);
            promise.completeExceptionally(error);
        }
    }

    /** Runs {@code action} as a rollback and publishes the plan it returns. */
    public void rollback(CompletableFuture<UpdatePlanSnapshot> promise, UpdateActor actor, @Nullable UUID failureId,
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

    private void stage(UpdatePlanSnapshot snapshot, UpdateActor actor, String operation) throws IOException {
        if (closed.getAsBoolean()) {
            throw new IllegalStateException("Update orchestrator is closed");
        }
        hooks.getStageAction().accept(snapshot);
        state.publish(new UpdatePlanSnapshot(snapshot.getId(), state.nextRevision(), UpdateState.UPDATE_STAGED,
                snapshot.getPlan(), snapshot.getBlockers(), snapshot.getMessage()), actor, operation);
    }
}
