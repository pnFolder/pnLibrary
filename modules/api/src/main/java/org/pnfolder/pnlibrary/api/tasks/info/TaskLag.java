package org.pnfolder.pnlibrary.api.tasks.info;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** How late one run of a task started, such as after the server froze. */
public final class TaskLag {
    private final TaskId taskId;
    private final String taskName;
    private final long runNumber;
    private final Instant planned;
    private final Instant started;
    private final Duration interval;

    /**
     * Describes one late run.
     *
     * @param taskId the task that is late
     * @param taskName its name, when it has one
     * @param runNumber the one-based number of the run that is about to start
     * @param planned when the run should have started
     * @param started when it actually starts
     * @param interval the repeat interval of the task, or {@code null} for a one-shot task
     */
    public TaskLag(
            @NotNull TaskId taskId,
            @Nullable String taskName,
            long runNumber,
            @NotNull Instant planned,
            @NotNull Instant started,
            @Nullable Duration interval) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.runNumber = runNumber;
        this.planned = planned;
        this.started = started;
        this.interval = interval;
    }

    /** The task that is late. */
    @NotNull
    public TaskId getTaskId() {
        return taskId;
    }

    /** Its name, when it has one. */
    @Nullable
    public String getTaskName() {
        return taskName;
    }

    /** The one-based number of the run that is about to start. */
    public long getRunNumber() {
        return runNumber;
    }

    /** When the run should have started. */
    @NotNull
    public Instant getPlanned() {
        return planned;
    }

    /** When it actually starts. */
    @NotNull
    public Instant getStarted() {
        return started;
    }

    /** The repeat interval of the task, or {@code null} for a one-shot task. */
    @Nullable
    public Duration getInterval() {
        return interval;
    }

    /** How late the run is; never negative. */
    @NotNull
    public Duration getLag() {
        Duration lag = Duration.between(planned, started);
        return lag.isNegative() ? Duration.ZERO : lag;
    }

    /** How many whole runs of a repeating task fitted into the delay and were lost; {@code 0} for a one-shot task. */
    public long getMissedRuns() {
        if (interval == null || interval.isZero()) {
            return 0L;
        }
        return getLag().toMillis() / interval.toMillis();
    }

    /** Whether the run is late by more than {@code threshold}. */
    public boolean isOver(@NotNull Duration threshold) {
        return getLag().compareTo(threshold) > 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskLag)) {
            return false;
        }
        TaskLag that = (TaskLag) other;
        return runNumber == that.runNumber
                && taskId.equals(that.taskId)
                && Objects.equals(taskName, that.taskName)
                && planned.equals(that.planned)
                && started.equals(that.started)
                && Objects.equals(interval, that.interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, taskName, runNumber, planned, started, interval);
    }

    /** A short description for logs and messages, such as {@code Autosave started 20s late (about 20 runs missed)}. */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        text.append(taskName != null ? taskName : taskId.toString())
                .append(" started ")
                .append(getLag().getSeconds())
                .append("s late");
        long missed = getMissedRuns();
        if (missed > 0) {
            text.append(" (about ").append(missed).append(" runs missed)");
        }
        return text.toString();
    }
}
