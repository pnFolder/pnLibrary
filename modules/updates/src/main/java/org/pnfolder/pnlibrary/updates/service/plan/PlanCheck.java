package org.pnfolder.pnlibrary.updates.service.plan;

import org.pnfolder.pnlibrary.updates.service.UpdateOrchestratorHooks;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.engine.ResolutionResult;

/** One check for updates: resolve, publish the result and stage it when the policy allows. */
public final class PlanCheck {
    private final UpdateOrchestratorHooks hooks;
    private final PlanState state;
    private final PlanChanges changes;
    private final BooleanSupplier closed;

    public PlanCheck(UpdateOrchestratorHooks hooks, PlanState state, PlanChanges changes, BooleanSupplier closed) {
        this.hooks = hooks;
        this.state = state;
        this.changes = changes;
        this.closed = closed;
    }

    /**
     * Runs the check and completes {@code promise} with the plan, or with a failed plan when the check broke.
     *
     * @param release gives back the slot that coalesces concurrent checks
     */
    public void run(CompletableFuture<UpdatePlanSnapshot> promise, boolean forceRemote, UpdateActor actor, Runnable release) {
        if (promise.isDone()) {
            release.run();
            return;
        }
        try {
            Attempt<UpdatePlanSnapshot> attempt = Failures.runCatchingNonFatal(() -> resolveAndStage(forceRemote, actor));
            UpdatePlanSnapshot completed = attempt.getOrNull();
            if (attempt.isFailure()) {
                Throwable error = attempt.exceptionOrNull();
                if (closed.getAsBoolean()) {
                    promise.completeExceptionally(error);
                    return;
                }
                completed = state.failedSnapshot(error);
                state.publish(completed, actor, "CHECK");
            }
            release.run();
            promise.complete(completed);
        } catch (Throwable error) {
            promise.completeExceptionally(error);
        } finally {
            release.run();
        }
    }

    private UpdatePlanSnapshot resolveAndStage(boolean forceRemote, UpdateActor actor) throws IOException {
        hooks.getBeforeCheck().run();
        UpdatePlanSnapshot checked = state.snapshotOf(resolve(forceRemote));
        if (closed.getAsBoolean()) {
            throw new IllegalStateException("Update orchestrator is closed");
        }
        state.publish(checked, actor, "CHECK");
        return changes.stageIfAutomatic(checked);
    }

    private ResolutionResult resolve(boolean forceRemote) {
        if (forceRemote && hooks.getRemoteResolver() != null) {
            return hooks.getRemoteResolver().get();
        }
        return hooks.getResolver().get();
    }
}
