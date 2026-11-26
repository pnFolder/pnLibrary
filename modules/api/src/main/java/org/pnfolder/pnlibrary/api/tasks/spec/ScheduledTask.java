package org.pnfolder.pnlibrary.api.tasks.spec;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.info.TaskLag;
import org.pnfolder.pnlibrary.api.tasks.run.LagResponse;

/**
 * A task written as a class: the settings are overridable getters, the arguments are constructor parameters.
 *
 * <p>It is the second way to describe a task next to {@link TaskSpec}: instead of a builder the task is its own type,
 * so it can hold its arguments, be tested alone and be scheduled anywhere with {@code scope.schedule(new MyTask(..))}.
 * Every setting has the same meaning as the same-named field of {@link TaskSpec}.
 *
 * <pre>{@code
 * class Autosave extends ScheduledTask {
 *     public String getName() { return "Autosave"; }
 *     public Duration getInterval() { return Duration.ofMinutes(5); }
 *     public void run(TaskContext context) { save(); }
 * }
 * tasks.schedule(new Autosave());
 * }</pre>
 */
public abstract class ScheduledTask implements TaskAction {
    /** Human-readable task name. */
    @Nullable
    public String getName() {
        return null;
    }

    /** What the task is for, shown by {@code /pn tasks}. */
    @Nullable
    public String getDescription() {
        return null;
    }

    /** How the task is used or what it changes. */
    @Nullable
    public String getUsage() {
        return null;
    }

    /** Conflict-resolution key within the owner scope. */
    @Nullable
    public String getKey() {
        return null;
    }

    /** Behavior when {@link #getKey()} is already registered. */
    @NotNull
    public TaskConflictPolicy getConflictPolicy() {
        return TaskConflictPolicy.REJECT;
    }

    /** Platform execution context. */
    @NotNull
    public TaskExecution getExecution() {
        return TaskExecution.global();
    }

    /** Delay before the first invocation. */
    @NotNull
    public Duration getDelay() {
        return Duration.ZERO;
    }

    /** Repeat interval, or {@code null} for a one-shot task. */
    @Nullable
    public Duration getInterval() {
        return null;
    }

    /** Longest a run may start late (for example after the server froze) before it is skipped; {@code null} never skips. */
    @Nullable
    public Duration getMaxLag() {
        return null;
    }

    /** Failures in a row a repeating task survives before it is stopped; {@code 0} stops it at the first. */
    public int getTolerateFailures() {
        return 0;
    }

    /** How late a run may start before {@link #onLag} is asked about it. */
    @NotNull
    public Duration getLagThreshold() {
        return Duration.ofSeconds(2);
    }

    /** For a task started by another task: whether it is cancelled together with that task. */
    public boolean getCancelWithParent() {
        return false;
    }

    /** Lookup tags. */
    @NotNull
    public Set<String> getTags() {
        return Collections.emptySet();
    }

    /** Whether an administrator may stop the task. */
    public boolean getCancellable() {
        return true;
    }

    /** Whether an administrator may start the task at once. */
    public boolean getRunnableNow() {
        return true;
    }

    /** Whether the task is left out of the commands and the menu. */
    public boolean getHidden() {
        return false;
    }

    /**
     * Called right before a run that starts more than {@link #getLagThreshold()} late, such as after the server froze.
     * Override it to tell an administrator or to skip a stale run; the default runs it anyway.
     */
    @NotNull
    public LagResponse onLag(@NotNull TaskLag lag) {
        return LagResponse.RUN;
    }

    /** Checked before each invocation; returning {@code false} skips that invocation. */
    public boolean shouldRun() {
        return true;
    }

    /** Checked before each invocation; returning {@code true} cancels the task. */
    public boolean shouldStop() {
        return false;
    }

    /** Builds the {@link TaskSpec} that describes this task as it is right now. */
    @NotNull
    public final TaskSpec toSpec() {
        return TaskSpec.builder()
                .name(getName())
                .description(getDescription())
                .usage(getUsage())
                .key(getKey())
                .conflictPolicy(getConflictPolicy())
                .execution(getExecution())
                .delay(getDelay())
                .interval(getInterval())
                .maxLag(getMaxLag())
                .tolerateFailures(getTolerateFailures())
                .onLag(getLagThreshold(), this::onLag)
                .cancelWithParent(getCancelWithParent())
                .tags(getTags())
                .cancellable(getCancellable())
                .runnableNow(getRunnableNow())
                .hidden(getHidden())
                .condition(this::shouldRun)
                .cancelWhen(this::shouldStop)
                .action(this)
                .build();
    }
}
