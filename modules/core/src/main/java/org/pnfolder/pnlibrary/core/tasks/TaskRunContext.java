package org.pnfolder.pnlibrary.core.tasks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** What one run of a task sees of the task: its identity, its schedule and a way to start or stop work. */
final class TaskRunContext implements TaskContext {
    private final ManagedTask task;
    private final long runNumber;
    private final Instant scheduledAt;
    private final Instant startedAt;
    private final Instant previousStartedAt;

    TaskRunContext(ManagedTask task, long runNumber, Instant scheduledAt, Instant startedAt, @Nullable Instant previousStartedAt) {
        this.task = task;
        this.runNumber = runNumber;
        this.scheduledAt = scheduledAt;
        this.startedAt = startedAt;
        this.previousStartedAt = previousStartedAt;
    }

    @NotNull
    @Override
    public TaskId getId() {
        return task.getId();
    }

    @Nullable
    @Override
    public String getName() {
        return task.getSpec().getName();
    }

    @Nullable
    @Override
    public String getKey() {
        return task.getSpec().getKey();
    }

    @NotNull
    @Override
    public Set<String> getTags() {
        return task.getSpec().getTags();
    }

    @NotNull
    @Override
    public TaskExecution.Kind getExecutionKind() {
        return task.getSpec().getExecution().getKind();
    }

    @NotNull
    @Override
    public TaskExecution getExecution() {
        return task.getSpec().getExecution();
    }

    @Nullable
    @Override
    public Duration getInterval() {
        return task.getSpec().getInterval();
    }

    @Override
    public long getRunNumber() {
        return runNumber;
    }

    @Override
    public long getSkippedCount() {
        return task.getSkippedCount();
    }

    @NotNull
    @Override
    public Instant getScheduledAt() {
        return scheduledAt;
    }

    @NotNull
    @Override
    public Instant getStartedAt() {
        return startedAt;
    }

    @Nullable
    @Override
    public Instant getPreviousStartedAt() {
        return previousStartedAt;
    }

    @Override
    public boolean isCancelled() {
        return task.isCancelled();
    }

    @Nullable
    @Override
    public TaskId getParentId() {
        return task.getParentId();
    }

    @Override
    public void cancel() {
        task.cancel();
    }

    @NotNull
    @Override
    public TaskSnapshot snapshot() {
        return task.snapshot();
    }

    @NotNull
    @Override
    public TaskHandle spawn(@NotNull TaskSpec spec) {
        return task.spawnChild(spec);
    }
}
