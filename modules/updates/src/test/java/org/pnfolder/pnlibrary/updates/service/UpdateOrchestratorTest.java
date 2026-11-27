package org.pnfolder.pnlibrary.updates.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.engine.ResolutionResult;
import org.pnfolder.pnlibrary.updates.history.UpdateStateStore;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;

class UpdateOrchestratorTest {
    private static final UpdateConfiguration MANUAL = new UpdateConfiguration();
    private static final UpdateConfiguration AUTOMATIC = new UpdateConfiguration();

    @TempDir
    Path directory;

    private UpdateOrchestrator orchestrator(UpdateConfiguration configuration, ScheduledExecutorService executor,
                                            UpdateOrchestratorHooks hooks) throws IOException {
        return new UpdateOrchestrator(configuration, new UpdateStateStore(directory), executor, hooks);
    }

    private static UpdateOrchestratorHooks.Builder hooks(java.util.function.Supplier<ResolutionResult> resolver) {
        return UpdateOrchestratorHooks.builder(resolver, snapshot -> { }, snapshot -> { });
    }

    private static Map.Entry<String, UpdateActor> observed(String operation, UpdateActor actor) {
        return new AbstractMap.SimpleEntry<>(operation, actor);
    }

    @Test
    void forcingAStagedLibraryBypassesABlockedGraphAndSurvivesChecksUntilRestart() throws Exception {
        List<UpdatePlanSnapshot> staged = new ArrayList<>();
        List<Map.Entry<String, UpdateActor>> observations = new ArrayList<>();
        ProductId library = ProductId.of("pnlibrary");
        UpdatePlan libraryOnly = new UpdatePlan(2, Collections.singletonList(new ProductChange(library,
                SemanticVersion.parse("1.0.0"), SemanticVersion.parse("2.0.0"))), Collections.emptyList());
        UpdateActor admin = new UpdateActor(UpdateActor.Kind.CONSOLE, "console", "Console");
        UpdateOrchestratorHooks hooks = UpdateOrchestratorHooks.builder(
                () -> new ResolutionResult.Blocked(Collections.singletonList(new BlockedReason.Frozen(library)), null),
                staged::add, snapshot -> { })
                .observeState((snapshot, actor, operation) -> observations.add(observed(operation, actor))).build();

        try (UpdateOrchestrator orchestrator = orchestrator(MANUAL, Executors.newSingleThreadScheduledExecutor(), hooks)) {
            UpdatePlanSnapshot blocked = orchestrator.checkNow().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(UpdateState.BLOCKED, blocked.getState());
            UpdatePlanSnapshot prepared = orchestrator.stageLibrary(() -> libraryOnly, admin)
                    .toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(UpdateState.UPDATE_STAGED, prepared.getState());
            assertEquals(libraryOnly, prepared.getPlan());
            assertEquals(prepared, orchestrator.checkNow().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(prepared, orchestrator.stageLibrary(() -> {
                throw new AssertionError("the already prepared transaction must be reused");
            }, admin).toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(1, staged.size());
            assertTrue(observations.contains(observed("FORCE_STAGE", admin)));
        }
        try (UpdateOrchestrator reopened = orchestrator(MANUAL, Executors.newSingleThreadScheduledExecutor(), hooks)) {
            assertEquals(UpdateState.BLOCKED, reopened.checkNow().toCompletableFuture().get(2, TimeUnit.SECONDS).getState());
        }
    }

    @Test
    @DisplayName("a check returning after shutdown cannot announce or automatically stage its result")
    void aCheckReturningAfterShutdownCannotAnnounceOrAutomaticallyStageItsResult() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger observed = new AtomicInteger();
        AtomicInteger staged = new AtomicInteger();
        AtomicInteger announced = new AtomicInteger();
        UpdateOrchestrator orchestrator = orchestrator(AUTOMATIC, executor, UpdateOrchestratorHooks.builder(() -> {
            entered.countDown();
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // keep waiting until released
                }
            }
            return new ResolutionResult.Ready(plan("2.0.0"));
        }, snapshot -> staged.incrementAndGet(), snapshot -> announced.incrementAndGet())
            .observeState((snapshot, actor, operation) -> observed.incrementAndGet()).build());
        try {
            CompletableFuture<UpdatePlanSnapshot> result = orchestrator.checkNow().toCompletableFuture();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            orchestrator.close();
            release.countDown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            assertTrue(result.isCompletedExceptionally());
            assertEquals(0, observed.get());
            assertEquals(0, staged.get());
            assertEquals(0, announced.get());
        } finally {
            release.countDown();
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("close completes queued operations without executing their mutations")
    void closeCompletesQueuedOperationsWithoutExecutingTheirMutations() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
                // the test ends the blocker by interrupting it
            }
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        AtomicInteger mutations = new AtomicInteger();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor, UpdateOrchestratorHooks.builder(
            () -> new ResolutionResult.Ready(plan("2.0.0")), snapshot -> mutations.incrementAndGet(), snapshot -> { })
            .automaticAllowed(snapshot -> false).build());
        try {
            CompletableFuture<UpdatePlanSnapshot> check = orchestrator.checkNow().toCompletableFuture();
            CompletableFuture<UpdatePlanSnapshot> stage = orchestrator.stage(UUID.randomUUID()).toCompletableFuture();
            CompletableFuture<UpdatePlanSnapshot> rollback = orchestrator.rollbackTo(UpdateActor.SYSTEM, () -> {
                mutations.incrementAndGet();
                throw new IllegalStateException("must not execute");
            }).toCompletableFuture();
            orchestrator.close();
            for (CompletableFuture<UpdatePlanSnapshot> future : Arrays.asList(check, stage, rollback)) {
                assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            }
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            assertEquals(0, mutations.get());
            assertTrue(orchestrator.stage(UUID.randomUUID()).toCompletableFuture().isCompletedExceptionally());
        } finally {
            release.countDown();
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("coalesces checks announces changed plans and rejects stale staging")
    void coalescesChecksAnnouncesChangedPlansAndRejectsStaleStaging() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<UpdatePlanSnapshot> announcements = new ArrayList<>();
        List<UpdatePlanSnapshot> staged = new ArrayList<>();
        AtomicReference<ResolutionResult> result = new AtomicReference<>(
            new ResolutionResult.Ready(plan("2.0.0")));
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor, UpdateOrchestratorHooks.builder(() -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return result.get();
        }, staged::add, announcements::add).automaticAllowed(snapshot -> false).build());
        try {
            CompletableFuture<UpdatePlanSnapshot> first = orchestrator.checkNow().toCompletableFuture();
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            CompletableFuture<UpdatePlanSnapshot> coalesced = orchestrator.checkNow().toCompletableFuture();
            release.countDown();
            UpdatePlanSnapshot old = first.get(2, TimeUnit.SECONDS);
            assertEquals(old.getId(), coalesced.get().getId());
            assertEquals(1, calls.get());
            assertEquals(1, announcements.size());

            result.set(new ResolutionResult.Ready(plan("3.0.0")));
            UpdatePlanSnapshot current = orchestrator.checkNow().toCompletableFuture().get();
            assertEquals(2, announcements.size());
            assertThrows(Exception.class, () -> orchestrator.stage(old.getId()).toCompletableFuture().get());
            assertEquals(UpdateState.UPDATE_STAGED, orchestrator.stage(current.getId()).toCompletableFuture().get().getState());
            assertEquals(1, staged.size());
            assertThrows(UnsupportedOperationException.class, () -> orchestrator.history().clear());
        } finally {
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("an unchanged state is announced once and reminders repeat it until the release is gone")
    void anUnchangedStateIsAnnouncedOnceAndRemindersRepeatItUntilTheReleaseIsGone() throws Exception {
        List<UpdatePlanSnapshot> announcements = new ArrayList<>();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean available = new AtomicBoolean(true);
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor, UpdateOrchestratorHooks.builder(
            () -> new ResolutionResult.Ready(available.get() ? plan("2.0.0")
                : new UpdatePlan(1, Collections.emptyList(), Collections.emptyList())),
            snapshot -> { }, announcements::add).automaticAllowed(snapshot -> false).build());
        try {
            orchestrator.checkNow().toCompletableFuture().get();
            orchestrator.checkNow().toCompletableFuture().get();
            assertEquals(1, announcements.size());
            orchestrator.remind();
            assertEquals(2, announcements.size());
            available.set(false);
            orchestrator.checkNow().toCompletableFuture().get();
            int afterGone = announcements.size();
            orchestrator.remind();
            assertEquals(afterGone, announcements.size());
        } finally {
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("executor rejection completes check and stage futures instead of leaking")
    void executorRejectionCompletesCheckAndStageFuturesInsteadOfLeaking() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor,
            hooks(() -> new ResolutionResult.Ready(plan("2.0.0"))).automaticAllowed(snapshot -> false).build());
        executor.shutdownNow();
        assertThrows(Exception.class, () -> orchestrator.checkNow().toCompletableFuture().get());
        assertThrows(Exception.class, () -> orchestrator.stage(UUID.randomUUID()).toCompletableFuture().get());
        assertDoesNotThrow(orchestrator::registrationsChanged);
        orchestrator.close();
    }

    @Test
    @DisplayName("manual rollback runs action and publishes rolled back state")
    void manualRollbackRunsActionAndPublishesRolledBackState() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger rollbackRuns = new AtomicInteger();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor,
            hooks(() -> new ResolutionResult.Ready(plan("2.0.0"))).automaticAllowed(snapshot -> false).build());
        try {
            UpdatePlanSnapshot available = orchestrator.checkNow().toCompletableFuture().get();

            UpdatePlanSnapshot restored = orchestrator.rollback(rollbackRuns::incrementAndGet).toCompletableFuture().get();

            assertEquals(1, rollbackRuns.get());
            assertEquals(UpdateState.ROLLED_BACK, restored.getState());
            assertEquals(available.getId(), restored.getId());
            assertEquals(UpdateState.ROLLED_BACK, orchestrator.currentPlan().get().getState());
        } finally {
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("forced manual check waits for cached check then resolves remotely")
    void forcedManualCheckWaitsForCachedCheckThenResolvesRemotely() throws Exception {
        CountDownLatch cachedEntered = new CountDownLatch(1);
        CountDownLatch cachedRelease = new CountDownLatch(1);
        AtomicInteger remoteCalls = new AtomicInteger();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor, hooks(() -> {
            cachedEntered.countDown();
            try {
                cachedRelease.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return new ResolutionResult.Ready(plan("2.0.0"));
        }).remoteResolver(() -> {
            remoteCalls.incrementAndGet();
            return new ResolutionResult.Ready(plan("3.0.0"));
        }).automaticAllowed(snapshot -> false).build());
        try {
            CompletableFuture<UpdatePlanSnapshot> cached = orchestrator.checkNow(false).toCompletableFuture();
            assertTrue(cachedEntered.await(1, TimeUnit.SECONDS));
            CompletableFuture<UpdatePlanSnapshot> forced = orchestrator.checkNow(true).toCompletableFuture();
            cachedRelease.countDown();

            assertEquals("2.0.0", cached.get(2, TimeUnit.SECONDS).getPlan().getChanges().get(0).getTo().toString());
            assertEquals("3.0.0", forced.get(2, TimeUnit.SECONDS).getPlan().getChanges().get(0).getTo().toString());
            assertEquals(1, remoteCalls.get());
        } finally {
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("failed remote check publishes durable failed snapshot with cause")
    void failedRemoteCheckPublishesDurableFailedSnapshotWithCause() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor, hooks(() -> {
            throw new IllegalStateException("GitHub вернул HTTP 404");
        }).automaticAllowed(snapshot -> false).build());
        try {
            UpdatePlanSnapshot failed = orchestrator.checkNow().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(UpdateState.FAILED, failed.getState());
            assertEquals("GitHub вернул HTTP 404", failed.getMessage());
            assertEquals(UpdateState.FAILED, new UpdateStateStore(directory).current().getState());
        } finally {
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("manual operations retain their initiator through the executor")
    void manualOperationsRetainTheirInitiatorThroughTheExecutor() throws Exception {
        UpdateActor actor = new UpdateActor(UpdateActor.Kind.CONSOLE, "console", "Console");
        List<Map.Entry<String, UpdateActor>> observations = new ArrayList<>();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor,
            hooks(() -> new ResolutionResult.Ready(plan("2.0.0")))
                .observeState((snapshot, recordedActor, operation) -> observations.add(observed(operation, recordedActor)))
                .automaticAllowed(snapshot -> false).build());
        try {
            UpdatePlanSnapshot plan = orchestrator.checkNow(actor).toCompletableFuture().get(2, TimeUnit.SECONDS);
            orchestrator.stage(plan.getId(), actor).toCompletableFuture().get(2, TimeUnit.SECONDS);
            orchestrator.rollback(actor, () -> { }).toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(Arrays.asList(observed("CHECK", actor), observed("STAGE", actor), observed("ROLLBACK", actor)),
                observations);
        } finally {
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("automatic staging is attributed to the system rather than the person checking")
    void automaticStagingIsAttributedToTheSystemRatherThanThePersonChecking() throws Exception {
        UpdateActor actor = new UpdateActor(UpdateActor.Kind.CONSOLE, "console", "Console");
        List<Map.Entry<String, UpdateActor>> observations = new ArrayList<>();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        UpdateOrchestrator orchestrator = orchestrator(AUTOMATIC, executor,
            hooks(() -> new ResolutionResult.Ready(plan("2.0.0")))
                .observeState((snapshot, recordedActor, operation) -> observations.add(observed(operation, recordedActor)))
                .build());
        try {
            orchestrator.checkNow(actor).toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(Arrays.asList(observed("CHECK", actor), observed("STAGE", UpdateActor.SYSTEM)), observations);
        } finally {
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("failed manual staging records its initiator without replacing the available plan")
    void failedManualStagingRecordsItsInitiatorWithoutReplacingTheAvailablePlan() throws Exception {
        UpdateActor actor = new UpdateActor(UpdateActor.Kind.CONSOLE, "console", "Console");
        List<UpdatePlanSnapshot> observations = new ArrayList<>();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor, UpdateOrchestratorHooks.builder(
            () -> new ResolutionResult.Ready(plan("2.0.0")), snapshot -> {
                throw new IllegalStateException("checksum mismatch");
            }, snapshot -> { }).observeState((snapshot, recordedActor, operation) -> {
                if (operation.equals("STAGE")) {
                    assertEquals(actor, recordedActor);
                    observations.add(snapshot);
                }
            }).automaticAllowed(snapshot -> false).build());
        try {
            UpdatePlanSnapshot selected = orchestrator.checkNow().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertThrows(Exception.class,
                () -> orchestrator.stage(selected.getId(), actor).toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(1, observations.size());
            assertEquals(UpdateState.FAILED, observations.get(0).getState());
            assertEquals("checksum mismatch", observations.get(0).getMessage());
            assertEquals(UpdateState.UPDATE_AVAILABLE, orchestrator.currentPlan().get().getState());
        } finally {
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("saved rollback can publish its own plan without an unrelated current plan")
    void savedRollbackCanPublishItsOwnPlanWithoutAnUnrelatedCurrentPlan() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor,
            hooks(() -> new ResolutionResult.Ready(plan("3.0.0"))).automaticAllowed(snapshot -> false).build());
        UUID id = UUID.randomUUID();
        try {
            UpdatePlanSnapshot saved = new UpdatePlanSnapshot(id, 0, UpdateState.ROLLED_BACK, plan("2.0.0"),
                Collections.emptyList(), "saved set");
            UpdatePlanSnapshot restored = orchestrator.rollbackTo(UpdateActor.SYSTEM, () -> saved)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(id, restored.getId());
            assertEquals("2.0.0", restored.getPlan().getChanges().get(0).getTo().toString());
            assertEquals("saved set", restored.getMessage());
            assertEquals(UpdateState.ROLLED_BACK, orchestrator.currentPlan().get().getState());
        } finally {
            orchestrator.close();
        }
    }

    @Test
    @DisplayName("a scheduled check that cannot save its result is reported instead of lost")
    void aScheduledCheckThatCannotSaveItsResultIsReportedInsteadOfLost() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        LinkedBlockingQueue<Map.Entry<String, Throwable>> reported = new LinkedBlockingQueue<>();
        UpdateOrchestrator orchestrator = orchestrator(MANUAL, executor, UpdateOrchestratorHooks.builder(
            () -> new ResolutionResult.Ready(plan("2.0.0")), snapshot -> { }, snapshot -> {
                throw new IllegalStateException("announcement broke");
            }).unexpectedFailure((message, error) -> reported.add(new AbstractMap.SimpleEntry<>(message, error))).automaticAllowed(snapshot -> false).build());
        try {
            orchestrator.start();
            Map.Entry<String, Throwable> entry = reported.poll(5, TimeUnit.SECONDS);
            assertNotNull(entry);
            assertEquals("Scheduled update check failed", entry.getKey());
            Throwable error = entry.getValue();
            String message = error.getCause() != null && error.getCause().getMessage() != null
                ? error.getCause().getMessage() : error.getMessage();
            assertEquals("announcement broke", message);
        } finally {
            orchestrator.close();
        }
    }

    private UpdatePlan plan(String version) {
        ProductId id = ProductId.of("pnlibrary");
        SemanticVersion semantic = SemanticVersion.parse(version);
        return new UpdatePlan(1, Collections.singletonList(new ProductChange(id, SemanticVersion.parse("1.0.0"), semantic)),
            Collections.singletonList(new ProductRelease(id, semantic, UpdateChannel.STABLE, new ApiVersionRange(1, 1), 1,
                Collections.emptyList(), null, Collections.emptyList(), Collections.emptyList(), null, null, "")));
    }
}
