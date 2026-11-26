package org.pnfolder.pnlibrary.api.tasks.run;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.spec.ScheduledTask;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskAction;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

/**
 * What a running task knows about itself: who it is, which run this is, and how to stop.
 *
 * <p>It is handed to {@link TaskAction#run} for each invocation and is valid only during that invocation.
 *
 * <pre>{@code
 * task.action(context -> {
 *     if (context.isFirstRun()) warmUp();
 *     if (context.every(10)) saveToDatabase();
 *     context.cancelAfter(100);
 * });
 * }</pre>
 */
public interface TaskContext {
    /** Tag put on every task started with {@link #later}. */
    String SUBTASK_TAG = "subtask";

    /** Stable identifier of the running task. */
    @NotNull
    TaskId getId();

    /** Optional human-readable task name. */
    @Nullable
    String getName();

    /** Conflict-resolution key of the task, or {@code null} when it has none. */
    @Nullable
    String getKey();

    /** Lookup tags of the task. */
    @NotNull
    Set<String> getTags();

    /** Platform execution context the invocation runs in. */
    @NotNull
    TaskExecution.Kind getExecutionKind();

    /** Platform execution context of this task, handed on to follow-up work started with {@link #later}. */
    @NotNull
    TaskExecution getExecution();

    /** Repeat interval, or {@code null} for a one-shot task. */
    @Nullable
    Duration getInterval();

    /** One-based invocation number. */
    long getRunNumber();

    /** How many invocations were skipped so far because a condition rejected them or the previous run was still going. */
    long getSkippedCount();

    /** Time at which this invocation was intended to start. */
    @NotNull
    Instant getScheduledAt();

    /** Time at which this invocation actually started. */
    @NotNull
    Instant getStartedAt();

    /** Start time of the previous invocation, or {@code null} on the first run. */
    @Nullable
    Instant getPreviousStartedAt();

    /** Whether the task has been cancelled, for example by an administrator while this run is going. */
    boolean isCancelled();

    /** The task that started this one with {@link #spawn} or {@link #later}, or {@code null} for a task scheduled directly. */
    @Nullable
    TaskId getParentId();

    /** Cancels all future invocations of this task. */
    void cancel();

    /** Returns a detached view of the whole task as it is right now. */
    @NotNull
    TaskSnapshot snapshot();

    /**
     * Schedules {@code spec} as a separate task in the same scope as this one, so it shares the owner and is cancelled
     * together with the scope. The new task is independent: cancelling this one does not stop it.
     *
     * @throws IllegalStateException when the scope is already closed
     */
    @NotNull
    TaskHandle spawn(@NotNull TaskSpec spec);

    /** Schedules a task written as a class as a separate task in the same scope; see {@link #spawn(TaskSpec)}. */
    @NotNull
    default TaskHandle spawn(@NotNull ScheduledTask task) {
        return spawn(task.toSpec());
    }

    /**
     * Runs {@code action} once after {@code delay} in this task's execution context, without building a
     * {@link TaskSpec}. The follow-up is a task of its own, named after this one with the tag {@code subtask}.
     *
     * <pre>{@code
     * context.later(Duration.ofSeconds(5), c -> player.sendMessage(Component.text("Five seconds later")));
     * }</pre>
     */
    @NotNull
    default TaskHandle later(@NotNull Duration delay, @NotNull TaskAction action) {
        return later(delay, getExecution(), action);
    }

    /** Like {@link #later(Duration, TaskAction)}, but in the given {@code execution} context, such as {@code TaskExecution.async()}. */
    @NotNull
    default TaskHandle later(@NotNull Duration delay, @NotNull TaskExecution execution, @NotNull TaskAction action) {
        return spawn(TaskSpec.builder()
                .name((getName() != null ? getName() : getId().toString()) + " / later")
                .tag(SUBTASK_TAG)
                .execution(execution)
                .delay(delay)
                .action(action)
                .build());
    }

    /** Whether this is the first invocation. */
    default boolean isFirstRun() {
        return getRunNumber() == 1L;
    }

    /** Whether the task repeats, that is, has an {@link #getInterval() interval}. */
    default boolean isRepeating() {
        return getInterval() != null;
    }

    /** How late this invocation started compared with its plan; never negative. A big value means the server is overloaded. */
    @NotNull
    default Duration getLag() {
        Duration lag = Duration.between(getScheduledAt(), getStartedAt());
        return lag.isNegative() ? Duration.ZERO : lag;
    }

    /** How many whole runs of a repeating task were lost to the delay, such as after a server freeze; {@code 0} for a one-shot task. */
    default long getMissedRuns() {
        Duration interval = getInterval();
        if (interval == null || interval.isZero()) {
            return 0L;
        }
        return getLag().toMillis() / interval.toMillis();
    }

    /** Whether this invocation started more than {@code threshold} after its plan. */
    default boolean isLate(@NotNull Duration threshold) {
        return getLag().compareTo(threshold) > 0;
    }

    /** How long this invocation has been running. */
    @NotNull
    default Duration getElapsed() {
        return Duration.between(getStartedAt(), Instant.now());
    }

    /** Whether this run is every {@code step}-th one: true on runs {@code step}, 2 x {@code step} and so on. */
    default boolean every(long step) {
        if (step <= 0) {
            throw new IllegalArgumentException("Step must be positive");
        }
        return getRunNumber() % step == 0L;
    }

    /** Cancels the task when this run is the {@code limit}-th or later; returns whether it did. */
    default boolean cancelAfter(long limit) {
        if (getRunNumber() < limit) {
            return false;
        }
        cancel();
        return true;
    }

    /**
     * Runs {@code action} {@code times} times, {@code every} apart, as a task of its own under this one; the first run
     * is after one {@code every}. It is cancelled together with this task, and stops by itself after the last run.
     *
     * <pre>{@code
     * context.repeat(5, Duration.ofSeconds(1), c -> broadcast(String.valueOf(5 - c.getRunNumber())));
     * }</pre>
     */
    @NotNull
    default TaskHandle repeat(long times, @NotNull Duration every, @NotNull final TaskAction action) {
        if (times <= 0) {
            throw new IllegalArgumentException("Times must be positive");
        }
        return spawn(TaskSpec.builder()
                .name((getName() != null ? getName() : getId().toString()) + " / repeat")
                .tag(SUBTASK_TAG)
                .execution(getExecution())
                .delay(every)
                .interval(every)
                .cancelWithParent(true)
                .action(child -> {
                    action.run(child);
                    child.cancelAfter(times);
                })
                .build());
    }
}
