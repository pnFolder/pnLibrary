package org.pnfolder.pnlibrary.core.tasks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * One scheduled task: runs its action, tracks its state and reports to the terminal callback once it can never run again.
 *
 * <p>Every change of the state is a compare-and-set, so a cancel racing with a run is never overwritten when the run
 * ends, and a run the platform had already queued does nothing once the task is over.
 */
public final class ManagedTask implements TaskHandle {
    private final Object owner;
    private final Object scopeToken;
    private final TaskSpec spec;
    private final TaskRunner runner;
    private final TaskErrorLogger errorLogger;
    private final Consumer<ManagedTask> onTerminal;
    private final BiFunction<TaskSpec, TaskId, TaskHandle> spawn;
    private final TaskId parentId;
    private final TaskId id = TaskId.random();
    private final AtomicReference<TaskStatus> state = new AtomicReference<TaskStatus>(TaskStatus.SCHEDULED);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<PlatformTaskHandle> platformHandle = new AtomicReference<PlatformTaskHandle>();
    private final ConcurrentLinkedQueue<TaskHandle> linkedChildren = new ConcurrentLinkedQueue<TaskHandle>();
    private final TaskRunStats stats = new TaskRunStats();
    private final Instant created = Instant.now();
    private volatile Instant nextRun;

    /** A task that cannot start other tasks. */
    public ManagedTask(
            @NotNull Object owner,
            @NotNull Object scopeToken,
            @NotNull TaskSpec spec,
            @NotNull PlatformTaskAdapter adapter,
            @NotNull TaskErrorLogger errorLogger,
            @NotNull Consumer<ManagedTask> onTerminal) {
        this(owner, scopeToken, spec, adapter, errorLogger, onTerminal, (child, parent) -> {
            throw new IllegalStateException("This task cannot start other tasks");
        }, null);
    }

    /** A task that starts the tasks it spawns through {@code spawn}, as children of {@code parentId}. */
    public ManagedTask(
            @NotNull Object owner,
            @NotNull Object scopeToken,
            @NotNull TaskSpec spec,
            @NotNull PlatformTaskAdapter adapter,
            @NotNull TaskErrorLogger errorLogger,
            @NotNull Consumer<ManagedTask> onTerminal,
            @NotNull BiFunction<TaskSpec, TaskId, TaskHandle> spawn,
            @Nullable TaskId parentId) {
        this.owner = owner;
        this.scopeToken = scopeToken;
        this.spec = spec;
        this.errorLogger = errorLogger;
        this.onTerminal = onTerminal;
        this.spawn = spawn;
        this.parentId = parentId;
        this.nextRun = created.plus(spec.getDelay());
        this.runner = new TaskRunner(this, spec, stats, adapter, created);
    }

    @NotNull
    public Object getOwner() {
        return owner;
    }

    @NotNull
    public Object getScopeToken() {
        return scopeToken;
    }

    @NotNull
    public TaskSpec getSpec() {
        return spec;
    }

    @Nullable
    TaskId getParentId() {
        return parentId;
    }

    long getSkippedCount() {
        return stats.getSkipped();
    }

    @NotNull
    @Override
    public TaskId getId() {
        return id;
    }

    @NotNull
    @Override
    public TaskStatus getStatus() {
        return state.get();
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Hands the task the platform's handle, which is cancelled right away if the task is already over. */
    public void attach(@NotNull PlatformTaskHandle handle) {
        platformHandle.set(handle);
        if (getStatus().isTerminal()) {
            handle.cancel();
        }
    }

    /** The run the platform scheduled. */
    public void invoke() {
        runner.invoke();
    }

    /** Starts a waiting task now, on its own execution context, and leaves its schedule as it was. */
    public boolean runNow() {
        return runner.runNow();
    }

    @Nullable
    Instant getNextRun() {
        return nextRun;
    }

    void setNextRun(@Nullable Instant next) {
        nextRun = next;
    }

    /** Moves from waiting to running; false when the task is over or already running. */
    boolean startRunning() {
        return state.compareAndSet(TaskStatus.SCHEDULED, TaskStatus.RUNNING);
    }

    String getLabel() {
        return spec.getName() != null ? spec.getName() : id.toString();
    }

    /** A broken logger must not change what happens to the task; its failure rides along on the original error. */
    void log(String message, Throwable error) {
        try {
            errorLogger.log(owner, message, error);
        } catch (Throwable loggerFailure) {
            if (loggerFailure != error) {
                error.addSuppressed(loggerFailure);
            }
        }
    }

    /** Starts {@code child} under this task; a child that asked for it is cancelled when this task is. */
    @NotNull
    TaskHandle spawnChild(TaskSpec child) {
        TaskHandle handle = spawn.apply(child, id);
        if (child.getCancelWithParent()) {
            linkedChildren.removeIf(linked -> linked.getStatus().isTerminal());
            linkedChildren.add(handle);
            if (cancelled.get()) {
                handle.cancel();
            }
        }
        return handle;
    }

    /** Back to waiting after a run, planned for {@code next}; does nothing if the task ended meanwhile. */
    void waitUntil(@Nullable Instant next) {
        if (state.compareAndSet(TaskStatus.RUNNING, TaskStatus.SCHEDULED)) {
            nextRun = next;
        }
    }

    void fail(Throwable error) {
        boolean failed = end(TaskStatus.FAILED);
        if (failed) {
            stats.setFailure(TaskNames.describe(error));
        }
        TaskCleanup.suppressInto(error, this::cancelPlatformHandle,
                () -> errorLogger.log(owner, "[pnLibrary/tasks] Task " + getLabel() + " failed", error));
        if (failed) {
            onTerminal.accept(this);
        }
    }

    void cancelPlatformHandle() {
        PlatformTaskHandle handle = platformHandle.get();
        if (handle != null) {
            handle.cancel();
        }
    }

    void complete() {
        if (end(TaskStatus.COMPLETED)) {
            onTerminal.accept(this);
        }
    }

    @Override
    public void cancel() {
        cancelIfActive();
    }

    @Override
    public boolean cancelIfActive() {
        if (!end(TaskStatus.CANCELLED)) {
            return false;
        }
        cancelled.set(true);
        try {
            cancelPlatformHandle();
        } finally {
            for (TaskHandle child : linkedChildren) {
                child.cancel();
            }
            onTerminal.accept(this);
        }
        return true;
    }

    /** Moves the task to the final {@code status}; false when it was already over. */
    private boolean end(TaskStatus status) {
        TaskStatus previous = state.getAndUpdate(current -> current.isTerminal() ? current : status);
        if (previous.isTerminal()) {
            return false;
        }
        stats.setCompleted(Instant.now());
        return true;
    }

    @NotNull
    @Override
    public TaskSnapshot snapshot() {
        return TaskSnapshot.builder(id, TaskNames.ownerName(owner), spec.getExecution().getKind(), getStatus(), created)
                .name(spec.getName())
                .key(spec.getKey())
                .tags(spec.getTags())
                .nextRunAt(nextRun)
                .runCount(stats.getRuns())
                .skippedCount(stats.getSkipped())
                .failedCount(stats.getFailedRuns())
                .parentId(parentId)
                .lastLag(stats.getLastLag())
                .worstLag(stats.getWorstLag())
                .lastStartedAt(stats.getStarted())
                .lastCompletedAt(stats.getCompleted())
                .lastFailure(stats.getFailure())
                .description(spec.getDescription())
                .usage(spec.getUsage())
                .source(TaskNames.sourceOf(spec.getAction().getClass()))
                .interval(spec.getInterval())
                .lastDuration(stats.getLastDuration())
                .cancellable(spec.getCancellable())
                .runnableNow(spec.getRunnableNow())
                .hidden(spec.getHidden())
                .build();
    }
}
