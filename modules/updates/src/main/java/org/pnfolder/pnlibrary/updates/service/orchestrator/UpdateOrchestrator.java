package org.pnfolder.pnlibrary.updates.service.orchestrator;

import org.pnfolder.pnlibrary.updates.service.plan.PlanState;
import org.pnfolder.pnlibrary.updates.service.plan.PlanCheck;
import org.pnfolder.pnlibrary.updates.service.plan.PlanChanges;
import java.util.Objects;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.history.state.UpdateStateStore;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;
import org.pnfolder.pnlibrary.updates.policy.UpdateDefaults;

/** Runs update checks on a schedule or on request, keeps the current plan and stages automatic updates. */
public final class UpdateOrchestrator implements AutoCloseable {
    private final UpdateConfiguration configuration;
    private final ScheduledExecutorService executor;
    private final UpdateOrchestratorHooks hooks;
    private final Duration reminderInterval;
    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Set<CompletableFuture<UpdatePlanSnapshot>> pending = ConcurrentHashMap.newKeySet();
    private final PlanState state;
    private final PlanChanges changes;
    private final PlanCheck check;
    private CompletableFuture<UpdatePlanSnapshot> inFlight;
    private boolean inFlightForced;

    /** Creates an orchestrator that repeats unchanged announcements at the default interval. */
    public UpdateOrchestrator(@NotNull UpdateConfiguration configuration, @NotNull UpdateStateStore store,
                              @NotNull ScheduledExecutorService executor, @NotNull UpdateOrchestratorHooks hooks)
            throws IOException {
        this(configuration, store, executor, hooks, UpdateDefaults.REMINDER_INTERVAL);
    }

    /** Creates an orchestrator that repeats unchanged announcements every {@code reminderInterval}. */
    public UpdateOrchestrator(@NotNull UpdateConfiguration configuration, @NotNull UpdateStateStore store,
                              @NotNull ScheduledExecutorService executor, @NotNull UpdateOrchestratorHooks hooks,
                              @NotNull Duration reminderInterval) throws IOException {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.reminderInterval = Objects.requireNonNull(reminderInterval, "reminderInterval");
        this.state = new PlanState(store, hooks, closed::get);
        this.changes = new PlanChanges(configuration, hooks, state, closed::get);
        this.check = new PlanCheck(hooks, state, changes, closed::get);
    }

    private CompletableFuture<UpdatePlanSnapshot> operationPromise() {
        CompletableFuture<UpdatePlanSnapshot> promise = new CompletableFuture<>();
        pending.add(promise);
        promise.whenComplete((result, error) -> pending.remove(promise));
        // Recheck after registration so concurrent close cannot miss this request.
        if (closed.get()) {
            promise.completeExceptionally(new IllegalStateException("Update orchestrator is closed"));
        }
        return promise;
    }

    /** Begins periodic checks. */
    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }
        executor.scheduleWithFixedDelay(this::scheduledCheck, 0, configuration.getChecks().getInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(() -> Failures.runCatchingNonFatal(() -> {
            remind();
            return null;
        }).onFailure(error -> hooks.getUnexpectedFailure().accept("Update reminder failed", error)),
                reminderInterval.toMillis(), reminderInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void scheduledCheck() {
        checkNow(false, UpdateActor.SYSTEM).whenComplete((result, error) -> {
            // A closed orchestrator rejects the check on purpose; any other failure means the state could not be saved.
            if (error != null && !closed.get()) {
                hooks.getUnexpectedFailure().accept("Scheduled update check failed", error);
            }
        });
    }

    /**
     * Repeats the announcement of an update that is still waiting. It works on the state already known, so it never
     * asks GitHub; once a check finds the release gone, the state is up to date and the reminders stop.
     */
    public void remind() {
        UpdatePlanSnapshot snapshot = state.current();
        if (snapshot == null || closed.get()) {
            return;
        }
        if (snapshot.getState() == UpdateState.UPDATE_AVAILABLE || snapshot.getState() == UpdateState.UPDATE_STAGED) {
            hooks.getReminder().accept(snapshot);
        }
    }

    /** Checks for updates right away, from fresh catalogs, and returns the resulting plan. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> checkNow() {
        return checkNow(true, UpdateActor.UNKNOWN);
    }

    /** Checks for updates right away on behalf of {@code actor}, from fresh catalogs. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> checkNow(@NotNull UpdateActor actor) {
        return checkNow(true, actor);
    }

    /** Checks for updates right away; {@code forceRemote} skips the catalog cache. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> checkNow(boolean forceRemote) {
        return checkNow(forceRemote, UpdateActor.UNKNOWN);
    }

    /** Checks for updates right away on behalf of {@code actor}; {@code forceRemote} skips the catalog cache. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> checkNow(boolean forceRemote, @NotNull UpdateActor actor) {
        synchronized (lock) {
            if (closed.get()) {
                throw new IllegalStateException("Update orchestrator is closed");
            }
            if (inFlight != null) {
                CompletableFuture<UpdatePlanSnapshot> active = inFlight;
                if (!forceRemote || inFlightForced) {
                    return active;
                }
                return active.handle((result, error) -> null).thenCompose(ignored -> checkNow(true, actor));
            }
            CompletableFuture<UpdatePlanSnapshot> promise = operationPromise();
            inFlight = promise;
            inFlightForced = forceRemote;
            try {
                executor.execute(() -> check.run(promise, forceRemote, actor, () -> releaseInFlight(promise)));
            } catch (Throwable error) {
                releaseInFlight(promise);
                promise.completeExceptionally(error);
            }
            return promise;
        }
    }

    private void releaseInFlight(CompletableFuture<UpdatePlanSnapshot> promise) {
        synchronized (lock) {
            if (inFlight == promise) {
                inFlight = null;
                inFlightForced = false;
            }
        }
    }

    /** The latest plan, if one exists. */
    @NotNull
    public Optional<UpdatePlanSnapshot> currentPlan() {
        return state.currentPlan();
    }

    /** Queues a fresh graph check after any currently running check has released the coalescing slot. */
    public void registrationsChanged() {
        if (closed.get() || !started.get()) {
            return;
        }
        try {
            executor.execute(() -> {
                if (!closed.get()) {
                    scheduledCheck();
                }
            });
        } catch (RejectedExecutionException ignored) {
            // A concurrent executor shutdown is a normal lifecycle race. The owner
            // will close the orchestrator and no follow-up check is required.
        }
    }

    /** Downloads and prepares the plan with {@code planId}. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> stage(@NotNull UUID planId) {
        return stage(planId, UpdateActor.UNKNOWN);
    }

    /** Downloads and prepares the plan with {@code planId} on behalf of {@code actor}. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> stage(@NotNull UUID planId, @NotNull UpdateActor actor) {
        return queueStage(promise -> changes.stageRequested(promise, planId, actor));
    }

    /** Resolves and stages an administrator's library-only upgrade on the update executor. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> stageLibrary(@NotNull Supplier<UpdatePlan> resolve, @NotNull UpdateActor actor) {
        return queueStage(promise -> changes.stageLibraryRequested(promise, resolve, actor));
    }

    private CompletionStage<UpdatePlanSnapshot> queueStage(Consumer<CompletableFuture<UpdatePlanSnapshot>> action) {
        CompletableFuture<UpdatePlanSnapshot> promise = operationPromise();
        if (promise.isDone()) {
            return promise;
        }
        try {
            executor.execute(() -> action.accept(promise));
        } catch (Throwable error) {
            promise.completeExceptionally(error);
        }
        return promise;
    }

    /** Recent plans, newest first. */
    @NotNull
    public List<UpdatePlanSnapshot> history() {
        return state.history();
    }

    /** Runs {@code action} as a rollback and records the result. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> rollback(@NotNull Runnable action) {
        return rollback(UpdateActor.UNKNOWN, action);
    }

    /** Runs {@code action} as a rollback on behalf of {@code actor} and records the result. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> rollback(@NotNull UpdateActor actor, @NotNull Runnable action) {
        return rollbackTo(actor, () -> {
            UpdatePlanSnapshot selected = state.current();
            action.run();
            // A rollback needs saved files, not a plan: after a restart before any check there may be none.
            return new UpdatePlanSnapshot(selected == null ? UUID.randomUUID() : selected.getId(),
                    selected == null ? 0L : selected.getRevision(), UpdateState.ROLLED_BACK,
                    selected == null ? null : selected.getPlan(),
                    selected == null ? Collections.emptyList() : selected.getBlockers(),
                    "Предыдущие версии JAR подготовлены к следующему перезапуску.");
        });
    }

    /** Runs {@code action} as a rollback. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> rollbackTo(@NotNull UpdateActor actor,
                                                          @NotNull Callable<UpdatePlanSnapshot> action) {
        return rollbackTo(actor, null, action);
    }

    /** Runs {@code action} as a rollback, optionally of the failure {@code failureId}. */
    @NotNull
    public CompletionStage<UpdatePlanSnapshot> rollbackTo(@NotNull UpdateActor actor, @Nullable UUID failureId,
                                                          @NotNull Callable<UpdatePlanSnapshot> action) {
        CompletableFuture<UpdatePlanSnapshot> promise = operationPromise();
        if (promise.isDone()) {
            return promise;
        }
        try {
            executor.execute(() -> changes.rollback(promise, actor, failureId, action));
        } catch (Throwable error) {
            promise.completeExceptionally(error);
        }
        return promise;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (CompletableFuture<UpdatePlanSnapshot> promise : pending) {
            promise.completeExceptionally(new IllegalStateException("Update orchestrator is closed"));
        }
        executor.shutdownNow();
    }
}
