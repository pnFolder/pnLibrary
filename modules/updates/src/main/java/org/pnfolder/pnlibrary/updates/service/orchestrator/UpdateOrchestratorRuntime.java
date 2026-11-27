package org.pnfolder.pnlibrary.updates.service.orchestrator;

import org.pnfolder.pnlibrary.updates.service.report.UpdateAudit;
import org.pnfolder.pnlibrary.updates.service.registration.RegisteredUpdate;
import java.util.Objects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.lifecycle.PlatformReadiness;
import org.pnfolder.pnlibrary.updates.engine.resolver.ResolutionResult;
import org.pnfolder.pnlibrary.updates.engine.catalog.RefreshMode;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEvent;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEventChange;
import org.pnfolder.pnlibrary.updates.history.state.UpdateStateStore;
import org.pnfolder.pnlibrary.updates.install.UpdateInstaller;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;

/** Owns the scheduler and lifecycle wiring around the update orchestrator. */
public final class UpdateOrchestratorRuntime implements AutoCloseable {
    private static final int HISTORY_PAGE_SIZE = 10;

    private final PlatformLogging logging;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<CompletableFuture<?>> pendingReads = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(action -> {
        Thread thread = new Thread(action, "pnLibrary-update-orchestrator");
        thread.setDaemon(true);
        return thread;
    });
    private final UpdateInstallationLock installationLock;
    private final UpdateAudit audit;
    private final UpdateOrchestrator orchestrator;

    /**
     * Wires the orchestrator to the platform and starts verifying a prepared restart once the server is ready.
     *
     * @param registrations the registrations as they are now
     * @param resolveGraph resolves the plan with the given catalog refresh
     * @param stageGraph downloads and prepares a plan
     * @param announce tells administrators about a plan
     * @param remind repeats the news of a plan that is still waiting
     * @param automaticAllowed whether a plan may be staged without an administrator
     */
    public UpdateOrchestratorRuntime(@NotNull UpdateConfiguration configuration, @NotNull LibraryPaths paths,
                                     @NotNull PlatformLogging logging, @NotNull PlatformReadiness readiness,
                                     @NotNull UpdateInstaller installer,
                                     @NotNull Supplier<List<RegisteredUpdate>> registrations,
                                     @NotNull Function<RefreshMode, ResolutionResult> resolveGraph,
                                     @NotNull Consumer<UpdatePlanSnapshot> stageGraph,
                                     @NotNull Consumer<UpdatePlanSnapshot> announce,
                                     @NotNull Consumer<UpdatePlanSnapshot> remind,
                                     @NotNull Predicate<UpdatePlanSnapshot> automaticAllowed) throws IOException {
        this.logging = Objects.requireNonNull(logging, "logging");
        this.installationLock = UpdateInstallationLock.acquire(paths);
        this.audit = new UpdateAudit(paths, logging, this, closed::get);
        UpdateOrchestratorHooks hooks = UpdateOrchestratorHooks
                .builder(() -> resolveGraph.apply(RefreshMode.CACHED), stageGraph, announce)
                .reminder(remind)
                .automaticAllowed(automaticAllowed)
                .remoteResolver(() -> resolveGraph.apply(RefreshMode.FORCE_REMOTE))
                .beforeCheck(() -> reconcileRecovered(installer, registrations))
                .unexpectedFailure((message, error) -> {
                    if (!closed.get()) {
                        logging.log(this, LogLevel.ERROR, message, error);
                    }
                })
                .observeState(audit::recordState)
                .build();
        try {
            UpdateOrchestrator created = new UpdateOrchestrator(configuration,
                    new UpdateStateStore(paths.getUpdates(), message -> logging.log(this, LogLevel.WARNING, message)),
                    executor, hooks);
            installer.recoverInterruptedTransactions();
            readiness.whenServerReady(() -> beginStartupHealth(created, installer, registrations));
            this.orchestrator = created;
        } catch (Throwable error) {
            executor.shutdownNow();
            Failures.runCatchingNonFatal(() -> {
                releaseLockWhenIdle();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
    }

    /** The orchestrator that runs checks and operations. */
    @NotNull
    public UpdateOrchestrator getOrchestrator() {
        return orchestrator;
    }

    private void reconcileRecovered(UpdateInstaller installer, Supplier<List<RegisteredUpdate>> registrations) {
        try {
            installer.reconcileRecoveredFailures(registrations.get(), audit::recordHealth);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private void beginStartupHealth(UpdateOrchestrator created, UpdateInstaller installer,
                                    Supplier<List<RegisteredUpdate>> registrations) {
        // Proxy readiness can precede other plugins' initialization listeners. Wait for expected
        // managed registrations, with a deadline, rather than falsely marking them absent.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        if (closed.get()) {
            return;
        }
        try {
            executor.execute(() -> verifyStartup(created, installer, registrations, deadline));
        } catch (RejectedExecutionException error) {
            if (!closed.get()) {
                throw error;
            }
        }
    }

    private void verifyStartup(UpdateOrchestrator created, UpdateInstaller installer,
                               Supplier<List<RegisteredUpdate>> registrations, long deadline) {
        if (closed.get()) {
            return;
        }
        try {
            List<RegisteredUpdate> installed = registrations.get();
            if (installer.awaitingRegistrations(installed) && System.nanoTime() < deadline) {
                executor.schedule(() -> verifyStartup(created, installer, registrations, deadline), 250, TimeUnit.MILLISECONDS);
                return;
            }
            installer.reconcilePending(installed, audit::recordHealth);
            audit.observeComponents(installed);
            created.start();
        } catch (Exception error) {
            if (!closed.get()) {
                logging.log(this, LogLevel.CRITICAL,
                        "Could not verify restarted component versions; automatic checks were not started", error);
            }
        }
    }

    /** Records a change an administrator made to one component's settings and remembers it for the next start. */
    public void recordSettingsChange(@NotNull RegisteredUpdate registration, @NotNull String message,
                                     @NotNull UpdateActor actor) {
        audit.recordSettingsChange(registration, message, actor);
    }

    /** One page of history entries, read off the calling thread. */
    @NotNull
    public CompletionStage<List<UpdateHistoryEntry>> historyPage(int page, @Nullable String product) {
        return readAsync(() -> {
            if (page <= 0) {
                throw new IllegalArgumentException("Page must be positive");
            }
            List<UpdateHistoryEntry> entries = new ArrayList<>();
            for (UpdateEvent event : audit.events().read(page, HISTORY_PAGE_SIZE, normalized(product))) {
                entries.add(entry(event));
            }
            return Collections.unmodifiableList(entries);
        });
    }

    /** Number of history entries, optionally for one product. */
    @NotNull
    public CompletionStage<Integer> historyCount(@Nullable String product) {
        return readAsync(() -> audit.events().count(normalized(product)));
    }

    private static String normalized(@Nullable String product) {
        return product == null ? null : ProductId.of(product).getValue();
    }

    private static UpdateHistoryEntry entry(UpdateEvent event) {
        List<ProductChange> changes = new ArrayList<>();
        for (UpdateEventChange change : event.getChanges()) {
            changes.add(new ProductChange(ProductId.of(change.getProduct()),
                    change.getFrom() == null ? null : SemanticVersion.parse(change.getFrom()),
                    SemanticVersion.parse(change.getTo())));
        }
        return new UpdateHistoryEntry(event.getId(), event.getOccurredAt(), event.getPlanId(),
                UpdateState.valueOf(event.getState()), event.getActor(), event.getOperation(), changes, event.getMessage());
    }

    /** Runs a blocking read on the update executor. */
    @NotNull
    public <T> CompletionStage<T> readAsync(@NotNull Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        pendingReads.add(result);
        result.whenComplete((value, error) -> pendingReads.remove(result));
        try {
            if (closed.get()) {
                throw new IllegalStateException("Update service is closed");
            }
            executor.execute(() -> {
                if (result.isDone()) {
                    return;
                }
                Attempt<T> attempt = Failures.runCatchingNonFatal(action);
                if (attempt.isFailure()) {
                    result.completeExceptionally(attempt.exceptionOrNull());
                } else {
                    result.complete(attempt.getOrNull());
                }
            });
        } catch (Exception error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (CompletableFuture<?> read : pendingReads) {
            read.completeExceptionally(new IllegalStateException("Update service is closed"));
        }
        try {
            orchestrator.close();
        } finally {
            releaseLockWhenIdle();
        }
    }

    private void releaseLockWhenIdle() {
        // shutdownNow cannot interrupt every filesystem operation. Keep the offline installer out
        // until an already-running stage operation has actually stopped writing.
        if (executor.isTerminated()) {
            installationLock.close();
            return;
        }
        Thread releaser = new Thread(() -> {
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.DAYS);
                } catch (InterruptedException ignored) {
                    // The lock must remain held until writes stop.
                }
            }
            installationLock.close();
        }, "pnLibrary-update-lock-release");
        releaser.setDaemon(true);
        releaser.start();
    }
}
