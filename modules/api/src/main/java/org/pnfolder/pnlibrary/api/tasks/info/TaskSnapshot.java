package org.pnfolder.pnlibrary.api.tasks.info;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;

/**
 * Immutable detached view of a live or retained task. Create one with {@link #builder(TaskId, String,
 * TaskExecution.Kind, TaskStatus, Instant)}; the builder's optional fields default to {@code null}, {@code 0}, or
 * {@code true} for {@code cancellable} and {@code runnableNow}.
 */
public final class TaskSnapshot {
    private final TaskId id;
    private final String name;
    private final String key;
    private final String ownerName;
    private final TaskExecution.Kind executionKind;
    private final TaskStatus status;
    private final Set<String> tags;
    private final Instant createdAt;
    private final Instant nextRunAt;
    private final long runCount;
    private final long skippedCount;
    private final Instant lastStartedAt;
    private final Instant lastCompletedAt;
    private final String lastFailure;
    private final String description;
    private final String usage;
    private final String source;
    private final Duration interval;
    private final Duration lastDuration;
    private final boolean cancellable;
    private final boolean runnableNow;
    private final long failedCount;
    private final TaskId parentId;
    private final Duration lastLag;
    private final Duration worstLag;
    private final boolean hidden;

    private TaskSnapshot(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.key = b.key;
        this.ownerName = b.ownerName;
        this.executionKind = b.executionKind;
        this.status = b.status;
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(b.tags));
        this.createdAt = b.createdAt;
        this.nextRunAt = b.nextRunAt;
        this.runCount = b.runCount;
        this.skippedCount = b.skippedCount;
        this.lastStartedAt = b.lastStartedAt;
        this.lastCompletedAt = b.lastCompletedAt;
        this.lastFailure = b.lastFailure;
        this.description = b.description;
        this.usage = b.usage;
        this.source = b.source;
        this.interval = b.interval;
        this.lastDuration = b.lastDuration;
        this.cancellable = b.cancellable;
        this.runnableNow = b.runnableNow;
        this.failedCount = b.failedCount;
        this.parentId = b.parentId;
        this.lastLag = b.lastLag;
        this.worstLag = b.worstLag;
        this.hidden = b.hidden;
    }

    /**
     * Starts a snapshot with the five fields every task has.
     *
     * @param id stable task identifier
     * @param ownerName diagnostic name of the owning object
     * @param executionKind selected execution context
     * @param status current or terminal lifecycle state
     * @param createdAt time at which the task was registered
     */
    @NotNull
    public static Builder builder(
            @NotNull TaskId id,
            @NotNull String ownerName,
            @NotNull TaskExecution.Kind executionKind,
            @NotNull TaskStatus status,
            @NotNull Instant createdAt) {
        return new Builder(id, ownerName, executionKind, status, createdAt);
    }

    /** Starts a builder holding every value of this snapshot, for a changed copy. */
    @NotNull
    public Builder toBuilder() {
        Builder b = new Builder(id, ownerName, executionKind, status, createdAt);
        b.name = name;
        b.key = key;
        b.tags = new LinkedHashSet<>(tags);
        b.nextRunAt = nextRunAt;
        b.runCount = runCount;
        b.skippedCount = skippedCount;
        b.lastStartedAt = lastStartedAt;
        b.lastCompletedAt = lastCompletedAt;
        b.lastFailure = lastFailure;
        b.description = description;
        b.usage = usage;
        b.source = source;
        b.interval = interval;
        b.lastDuration = lastDuration;
        b.cancellable = cancellable;
        b.runnableNow = runnableNow;
        b.failedCount = failedCount;
        b.parentId = parentId;
        b.lastLag = lastLag;
        b.worstLag = worstLag;
        b.hidden = hidden;
        return b;
    }

    /** Stable task identifier. */ @NotNull public TaskId getId() { return id; }
    /** Optional human-readable task name. */ @Nullable public String getName() { return name; }
    /** Optional conflict-resolution key. */ @Nullable public String getKey() { return key; }
    /** Diagnostic name of the owning object. */ @NotNull public String getOwnerName() { return ownerName; }
    /** Selected execution context. */ @NotNull public TaskExecution.Kind getExecutionKind() { return executionKind; }
    /** Current or terminal lifecycle state. */ @NotNull public TaskStatus getStatus() { return status; }
    /** Immutable application-defined tags. */ @NotNull public Set<String> getTags() { return tags; }
    /** Time at which the task was registered. */ @NotNull public Instant getCreatedAt() { return createdAt; }
    /** Next planned invocation, or {@code null} when none remains. */ @Nullable public Instant getNextRunAt() { return nextRunAt; }
    /** Number of invocations that started. */ public long getRunCount() { return runCount; }
    /** Number of invocations skipped because a condition rejected them. */ public long getSkippedCount() { return skippedCount; }
    /** Most recent invocation start time. */ @Nullable public Instant getLastStartedAt() { return lastStartedAt; }
    /** Most recent successful completion time. */ @Nullable public Instant getLastCompletedAt() { return lastCompletedAt; }
    /** Rendered most recent failure, when one occurred. */ @Nullable public String getLastFailure() { return lastFailure; }
    /** Optional sentence on what the task is for. */ @Nullable public String getDescription() { return description; }
    /** Optional note on how the task is used. */ @Nullable public String getUsage() { return usage; }
    /** Class of the task action, as far as it can be told. */ @Nullable public String getSource() { return source; }
    /** Repeat interval, or {@code null} for a one-shot task. */ @Nullable public Duration getInterval() { return interval; }
    /** How long the latest finished invocation took. */ @Nullable public Duration getLastDuration() { return lastDuration; }
    /** Whether an administrator may stop the task from the commands and the menu. */ public boolean getCancellable() { return cancellable; }
    /** Whether an administrator may start the task at once from the commands and the menu. */ public boolean getRunnableNow() { return runnableNow; }
    /** Number of invocations that threw an error, including ones a task survived. */ public long getFailedCount() { return failedCount; }
    /** The task that started this one, or {@code null} for a task scheduled directly. */ @Nullable public TaskId getParentId() { return parentId; }
    /** How late the latest run started compared with its plan. */ @Nullable public Duration getLastLag() { return lastLag; }
    /** The largest delay of any run so far, such as from a server freeze. */ @Nullable public Duration getWorstLag() { return worstLag; }
    /** Whether the task asked to be left out of the commands and the menu. */ public boolean getHidden() { return hidden; }
    /** Whether the task can still run, that is, it has not reached a terminal {@link #getStatus() status}. */
    public boolean isActive() {
        return !status.isTerminal();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskSnapshot)) {
            return false;
        }
        TaskSnapshot that = (TaskSnapshot) other;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(key, that.key) && Objects.equals(ownerName, that.ownerName) && Objects.equals(executionKind, that.executionKind) && Objects.equals(status, that.status) && Objects.equals(tags, that.tags) && Objects.equals(createdAt, that.createdAt) && Objects.equals(nextRunAt, that.nextRunAt) && runCount == that.runCount && skippedCount == that.skippedCount && Objects.equals(lastStartedAt, that.lastStartedAt) && Objects.equals(lastCompletedAt, that.lastCompletedAt) && Objects.equals(lastFailure, that.lastFailure) && Objects.equals(description, that.description) && Objects.equals(usage, that.usage) && Objects.equals(source, that.source) && Objects.equals(interval, that.interval) && Objects.equals(lastDuration, that.lastDuration) && cancellable == that.cancellable && runnableNow == that.runnableNow && failedCount == that.failedCount && Objects.equals(parentId, that.parentId) && Objects.equals(lastLag, that.lastLag) && Objects.equals(worstLag, that.worstLag) && hidden == that.hidden;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, key, ownerName, executionKind, status, tags, createdAt, nextRunAt, runCount, skippedCount, lastStartedAt, lastCompletedAt, lastFailure, description, usage, source, interval, lastDuration, cancellable, runnableNow, failedCount, parentId, lastLag, worstLag, hidden);
    }

    @Override
    public String toString() {
        return "TaskSnapshot(id=" + id + ", name=" + name + ", status=" + status + ", runCount=" + runCount + ")";
    }

    /** Fluent builder for a {@link TaskSnapshot}. */
    public static final class Builder {
        private TaskId id;
        private String name = null;
        private String key = null;
        private String ownerName;
        private TaskExecution.Kind executionKind;
        private TaskStatus status;
        private Set<String> tags = new LinkedHashSet<>();
        private Instant createdAt;
        private Instant nextRunAt = null;
        private long runCount = 0L;
        private long skippedCount = 0L;
        private Instant lastStartedAt = null;
        private Instant lastCompletedAt = null;
        private String lastFailure = null;
        private String description = null;
        private String usage = null;
        private String source = null;
        private Duration interval = null;
        private Duration lastDuration = null;
        private boolean cancellable = true;
        private boolean runnableNow = true;
        private long failedCount = 0L;
        private TaskId parentId = null;
        private Duration lastLag = null;
        private Duration worstLag = null;
        private boolean hidden = false;

        private Builder(TaskId id, String ownerName, TaskExecution.Kind executionKind, TaskStatus status, Instant createdAt) {
            this.id = id;
            this.ownerName = ownerName;
            this.executionKind = executionKind;
            this.status = status;
            this.createdAt = createdAt;
        }

        /** Sets id. */ @NotNull public Builder id(@NotNull TaskId value) { id = value; return this; }
        /** Sets ownerName. */ @NotNull public Builder ownerName(@NotNull String value) { ownerName = value; return this; }
        /** Sets executionKind. */ @NotNull public Builder executionKind(@NotNull TaskExecution.Kind value) { executionKind = value; return this; }
        /** Sets status. */ @NotNull public Builder status(@NotNull TaskStatus value) { status = value; return this; }
        /** Sets createdAt. */ @NotNull public Builder createdAt(@NotNull Instant value) { createdAt = value; return this; }
        /** Sets name. */ @NotNull public Builder name(@Nullable String value) { name = value; return this; }
        /** Sets key. */ @NotNull public Builder key(@Nullable String value) { key = value; return this; }
        /** Sets the application-defined tags. */ @NotNull public Builder tags(@NotNull Set<String> value) { tags = new LinkedHashSet<>(value); return this; }
        /** Sets nextRunAt. */ @NotNull public Builder nextRunAt(@Nullable Instant value) { nextRunAt = value; return this; }
        /** Sets runCount. */ @NotNull public Builder runCount(long value) { runCount = value; return this; }
        /** Sets skippedCount. */ @NotNull public Builder skippedCount(long value) { skippedCount = value; return this; }
        /** Sets lastStartedAt. */ @NotNull public Builder lastStartedAt(@Nullable Instant value) { lastStartedAt = value; return this; }
        /** Sets lastCompletedAt. */ @NotNull public Builder lastCompletedAt(@Nullable Instant value) { lastCompletedAt = value; return this; }
        /** Sets lastFailure. */ @NotNull public Builder lastFailure(@Nullable String value) { lastFailure = value; return this; }
        /** Sets description. */ @NotNull public Builder description(@Nullable String value) { description = value; return this; }
        /** Sets usage. */ @NotNull public Builder usage(@Nullable String value) { usage = value; return this; }
        /** Sets source. */ @NotNull public Builder source(@Nullable String value) { source = value; return this; }
        /** Sets interval. */ @NotNull public Builder interval(@Nullable Duration value) { interval = value; return this; }
        /** Sets lastDuration. */ @NotNull public Builder lastDuration(@Nullable Duration value) { lastDuration = value; return this; }
        /** Sets cancellable. */ @NotNull public Builder cancellable(boolean value) { cancellable = value; return this; }
        /** Sets runnableNow. */ @NotNull public Builder runnableNow(boolean value) { runnableNow = value; return this; }
        /** Sets failedCount. */ @NotNull public Builder failedCount(long value) { failedCount = value; return this; }
        /** Sets parentId. */ @NotNull public Builder parentId(@Nullable TaskId value) { parentId = value; return this; }
        /** Sets lastLag. */ @NotNull public Builder lastLag(@Nullable Duration value) { lastLag = value; return this; }
        /** Sets worstLag. */ @NotNull public Builder worstLag(@Nullable Duration value) { worstLag = value; return this; }
        /** Sets hidden. */ @NotNull public Builder hidden(boolean value) { hidden = value; return this; }
        /** Creates the immutable snapshot. */
        @NotNull
        public TaskSnapshot build() {
            return new TaskSnapshot(this);
        }
    }
}
