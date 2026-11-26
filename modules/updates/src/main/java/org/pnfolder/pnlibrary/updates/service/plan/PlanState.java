package org.pnfolder.pnlibrary.updates.service.plan;

import org.pnfolder.pnlibrary.updates.service.UpdateOrchestratorHooks;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.engine.ResolutionResult;
import org.pnfolder.pnlibrary.updates.history.UpdateStateStore;

/** The current plan, the plans of this session, and the publication of a new state to its observers. */
public final class PlanState {
    private static final int SESSION_HISTORY = 100;

    private final UpdateStateStore store;
    private final UpdateOrchestratorHooks hooks;
    private final BooleanSupplier closed;
    private final Object lock = new Object();
    private final AtomicLong revision;
    private final ArrayDeque<UpdatePlanSnapshot> sessionHistory = new ArrayDeque<>();
    private UpdatePlanSnapshot current;
    private String announcedFingerprint;

    public PlanState(UpdateStateStore store, UpdateOrchestratorHooks hooks, BooleanSupplier closed) throws IOException {
        this.store = store;
        this.hooks = hooks;
        this.closed = closed;
        this.current = store.current();
        this.revision = new AtomicLong(current == null ? 0L : current.getRevision());
    }

    @Nullable
    public UpdatePlanSnapshot current() {
        synchronized (lock) {
            return current;
        }
    }

    public Optional<UpdatePlanSnapshot> currentPlan() {
        return Optional.ofNullable(current());
    }

    UUID currentIdOrRandom() {
        UpdatePlanSnapshot snapshot = current();
        return snapshot == null ? UUID.randomUUID() : snapshot.getId();
    }

    public List<UpdatePlanSnapshot> history() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(sessionHistory));
        }
    }

    long nextRevision() {
        return revision.incrementAndGet();
    }

    /** The state a resolution result means: the previous snapshot when nothing the administrator sees has changed. */
    UpdatePlanSnapshot snapshotOf(ResolutionResult result) {
        UpdatePlan plan = null;
        List<BlockedReason> blockers = Collections.emptyList();
        if (result instanceof ResolutionResult.Ready) {
            plan = ((ResolutionResult.Ready) result).getPlan();
        } else if (result instanceof ResolutionResult.Blocked) {
            plan = ((ResolutionResult.Blocked) result).getFallbackPlan();
            blockers = ((ResolutionResult.Blocked) result).getReasons();
        }
        UpdateState state = !blockers.isEmpty() ? UpdateState.BLOCKED
                : plan == null || plan.getChanges().isEmpty() ? UpdateState.UP_TO_DATE : UpdateState.UPDATE_AVAILABLE;
        String fingerprint = PlanFingerprint.of(state, plan, blockers);
        UpdatePlanSnapshot previous = current();
        // A staged plan is an available plan whose files are already prepared, so the same release keeps its state.
        UpdateState previousState = previous == null || previous.getState() != UpdateState.UPDATE_STAGED
                ? (previous == null ? null : previous.getState()) : UpdateState.UPDATE_AVAILABLE;
        if (previous != null
                && PlanFingerprint.of(previousState, previous.getPlan(), previous.getBlockers()).equals(fingerprint)) {
            return previous;
        }
        return new UpdatePlanSnapshot(UUID.randomUUID(), nextRevision(), state, plan, blockers, null);
    }

    UpdatePlanSnapshot failedSnapshot(Throwable error) {
        UpdatePlanSnapshot previous = current();
        return new UpdatePlanSnapshot(UUID.randomUUID(), nextRevision(), UpdateState.FAILED,
                previous == null ? null : previous.getPlan(),
                previous == null ? Collections.emptyList() : previous.getBlockers(),
                PlanFingerprint.rootMessage(error));
    }

    /** Makes {@code snapshot} current, saves it, tells the observer and announces it unless it is old news. */
    void publish(UpdatePlanSnapshot snapshot, UpdateActor actor, String operation) throws IOException {
        synchronized (lock) {
            current = snapshot;
            sessionHistory.addFirst(snapshot);
            while (sessionHistory.size() > SESSION_HISTORY) {
                sessionHistory.removeLast();
            }
        }
        store.save(snapshot);
        hooks.getObserveState().observe(snapshot, actor, operation);
        if (closed.getAsBoolean()) {
            return;
        }
        String fingerprint = PlanFingerprint.of(snapshot.getState(), snapshot.getPlan(), snapshot.getBlockers());
        if (fingerprint.equals(announcedFingerprint)) {
            return;
        }
        hooks.getAnnouncement().accept(snapshot);
        announcedFingerprint = fingerprint;
    }

    /** Records a rejected operation as an audit event; it never replaces the current valid plan. */
    void observeFailure(UUID planId, UpdateActor actor, String operation, Throwable error) {
        UpdatePlanSnapshot known = current();
        UpdatePlanSnapshot selected = known != null && known.getId().equals(planId) ? known : null;
        Failures.runCatchingNonFatal(() -> {
            hooks.getObserveState().observe(new UpdatePlanSnapshot(planId, selected == null ? 0 : selected.getRevision(),
                    UpdateState.FAILED, selected == null ? null : selected.getPlan(),
                    selected == null ? Collections.emptyList() : selected.getBlockers(),
                    PlanFingerprint.rootMessage(error)), actor, operation);
            return null;
        }).onFailure(failure -> {
            if (failure != error) {
                error.addSuppressed(failure);
            }
        });
    }
}
