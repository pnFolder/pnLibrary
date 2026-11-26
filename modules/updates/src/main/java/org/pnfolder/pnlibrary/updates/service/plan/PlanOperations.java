package org.pnfolder.pnlibrary.updates.service.plan;

import org.pnfolder.pnlibrary.updates.service.UpdateOrchestratorRuntime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.UpdatePlans;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;

/** Checking for updates and preparing a plan, as plugins and administrators ask for it. */
public final class PlanOperations implements UpdatePlans {
    private final UpdateOrchestratorRuntime runtime;

    public PlanOperations(UpdateOrchestratorRuntime runtime) {
        this.runtime = runtime;
    }

    @NotNull
    @Override
    public CompletionStage<UpdatePlanSnapshot> check(@NotNull UpdateActor actor) {
        return runtime.getOrchestrator().checkNow(actor);
    }

    @NotNull
    @Override
    public Optional<UpdatePlanSnapshot> current() {
        return runtime.getOrchestrator().currentPlan();
    }

    @NotNull
    @Override
    public List<UpdatePlanSnapshot> recent() {
        return runtime.getOrchestrator().history();
    }

    @NotNull
    @Override
    public CompletionStage<UpdatePlanSnapshot> stage(@NotNull UUID planId, @NotNull UpdateActor actor) {
        return runtime.getOrchestrator().stage(planId, actor);
    }
}
