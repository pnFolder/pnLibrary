package org.pnfolder.pnlibrary.core.tasks;

import org.pnfolder.pnlibrary.api.tasks.info.TaskLag;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.run.LagResponse;
import org.pnfolder.pnlibrary.api.tasks.run.TaskLagHandler;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Carries out the runs of one task: checks the conditions and the lag, runs the action and handles its failure. */
final class TaskRunner {
    private static final Runnable NOTHING = new Runnable() {
        @Override
        public void run() {
        }
    };

    private final ManagedTask task;
    private final TaskSpec spec;
    private final TaskRunStats stats;
    private final PlatformTaskAdapter adapter;
    private final Instant created;
    private final AtomicBoolean running = new AtomicBoolean(false);

    TaskRunner(ManagedTask task, TaskSpec spec, TaskRunStats stats, PlatformTaskAdapter adapter, Instant created) {
        this.task = task;
        this.spec = spec;
        this.stats = stats;
        this.adapter = adapter;
        this.created = created;
    }

    /** The run the platform scheduled. */
    void invoke() {
        exclusively(stats::skip, this::invokeScheduled);
    }

    /** Starts a waiting task now, on its own execution context, and leaves its schedule as it was. */
    boolean runNow() {
        if (task.getStatus() != TaskStatus.SCHEDULED) {
            return false;
        }
        adapter.buildTask()
                .task(() -> exclusively(NOTHING, this::runRequested))
                .execution(spec.getExecution().getKind(), spec.getExecution().getTarget())
                .schedule();
        return true;
    }

    private void invokeScheduled() {
        Instant planned = task.getNextRun() != null ? task.getNextRun() : created.plus(spec.getDelay());
        task.setNextRun(null);
        if (anyIs(spec.getCancellationConditions(), true)) {
            task.cancel();
        } else if (anyIs(spec.getConditions(), false) || tooLate(planned)) {
            skip();
        } else {
            runUnlessLag(planned);
        }
    }

    private void runUnlessLag(Instant planned) {
        LagResponse response = lagResponse(planned);
        if (response == LagResponse.CANCEL) {
            task.cancel();
        } else if (response == LagResponse.SKIP) {
            skip();
        } else {
            runAction(planned);
            if (spec.getInterval() == null) {
                task.complete();
            } else {
                task.waitUntil(Instant.now().plus(spec.getInterval()));
            }
        }
    }

    private void runRequested() {
        Instant plannedNext = task.getNextRun();
        runAction(Instant.now());
        if (spec.getInterval() != null) {
            task.waitUntil(plannedNext);
            return;
        }
        task.complete();
        task.cancelPlatformHandle();
    }

    /** Whether some condition of {@code conditions} evaluates to {@code expected}; stops at the first. */
    private static boolean anyIs(List<BooleanSupplier> conditions, boolean expected) {
        for (BooleanSupplier condition : conditions) {
            if (condition.getAsBoolean() == expected) {
                return true;
            }
        }
        return false;
    }

    /** Runs {@code body} unless the task is over; while another run is still going, runs {@code onBusy} instead. */
    private void exclusively(Runnable onBusy, Runnable body) {
        if (task.getStatus().isTerminal()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            onBusy.run();
            return;
        }
        try {
            body.run();
        } catch (Throwable error) {
            stats.failRun();
            if (!survive(error)) {
                task.fail(error);
            }
        } finally {
            running.set(false);
        }
    }

    /** A condition said no: the run is skipped, and a one-time task is over. */
    private void skip() {
        stats.skip();
        if (spec.getInterval() == null) {
            task.complete();
        } else {
            task.setNextRun(Instant.now().plus(spec.getInterval()));
        }
    }

    /** Notes how late the run is and lets the lag handler decide what happens to it. */
    private LagResponse lagResponse(Instant planned) {
        TaskLag report = new TaskLag(task.getId(), spec.getName(), stats.getRuns() + 1, planned, Instant.now(), spec.getInterval());
        stats.lag(report.getLag());
        TaskLagHandler handler = spec.getLagHandler();
        if (handler == null || !report.isOver(spec.getLagThreshold())) {
            return LagResponse.RUN;
        }
        try {
            return handler.handle(report);
        } catch (Throwable error) {
            task.log("[pnLibrary/tasks] Lag handler of task " + task.getLabel() + " failed", error);
            return LagResponse.RUN;
        }
    }

    /** Whether a run planned for {@code planned} started later than the task allows. */
    private boolean tooLate(Instant planned) {
        Duration maxLag = spec.getMaxLag();
        return maxLag != null && Duration.between(planned, Instant.now()).compareTo(maxLag) > 0;
    }

    /** A repeating task may survive some failures in a row: the failure is logged and the next interval tries again. */
    private boolean survive(Throwable error) {
        Duration interval = spec.getInterval();
        if (interval == null) {
            return false;
        }
        long streak = stats.failInARow();
        if (streak > spec.getTolerateFailures()) {
            return false;
        }
        stats.setFailure(TaskNames.describe(error));
        task.log("[pnLibrary/tasks] Task " + task.getLabel() + " failed (" + streak + " of " + spec.getTolerateFailures()
                + " tolerated), trying again", error);
        task.waitUntil(Instant.now().plus(interval));
        return true;
    }

    private void runAction(Instant plannedAt) {
        if (!task.startRunning()) {
            return;
        }
        Instant previousStart = stats.getStarted();
        Instant runStartedAt = Instant.now();
        stats.setStarted(runStartedAt);
        long number = stats.startRun();
        long clock = System.nanoTime();
        spec.getAction().run(new TaskRunContext(task, number, plannedAt, runStartedAt, previousStart));
        stats.succeeded(Duration.ofNanos(System.nanoTime() - clock));
    }
}
