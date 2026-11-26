package org.pnfolder.pnlibrary.api.tasks.spec;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.info.TaskLag;
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext;
import org.pnfolder.pnlibrary.api.tasks.run.TaskLagHandler;

/**
 * Property-style configuration of a {@link TaskSpec} for Kotlin callers: a block sets properties and calls, then
 * {@link #schedule} or {@link #spawn} builds and starts the task.
 */
public final class TaskSpecBuilder {
    private final TaskSpec.Builder delegate = TaskSpec.builder();

    TaskSpecBuilder() {}

    /** Builds and schedules a task configured by {@code configure}. */
    @NotNull
    public static TaskHandle schedule(@NotNull TaskScope scope, @NotNull Consumer<TaskSpecBuilder> configure) {
        return scope.schedule(configured(configure));
    }

    /** Builds and starts a task of its own under the running task, configured by {@code configure}. */
    @NotNull
    public static TaskHandle spawn(@NotNull TaskContext context, @NotNull Consumer<TaskSpecBuilder> configure) {
        return context.spawn(configured(configure));
    }

    private static TaskSpec configured(Consumer<TaskSpecBuilder> configure) {
        TaskSpecBuilder builder = new TaskSpecBuilder();
        configure.accept(builder);
        return builder.delegate.build();
    }

    /** Optional human-readable task name. */
    @Nullable public String getName() { return delegate.name; }
    /** Sets name. */
    public void setName(@Nullable String value) { delegate.name(value); }

    /** Optional sentence on what the task is for. */
    @Nullable public String getDescription() { return delegate.description; }
    /** Sets description. */
    public void setDescription(@Nullable String value) { delegate.description(value); }

    /** Optional note on how the task is used or what it changes. */
    @Nullable public String getUsage() { return delegate.usage; }
    /** Sets usage. */
    public void setUsage(@Nullable String value) { delegate.usage(value); }

    /** Whether an administrator may stop the task from the commands and the menu; {@code true} by default. */
    public boolean getCancellable() { return delegate.cancellable; }
    /** Sets cancellable. */
    public void setCancellable(boolean value) { delegate.cancellable(value); }

    /** Whether an administrator may start the task at once from the commands and the menu; {@code true} by default. */
    public boolean getRunnableNow() { return delegate.runnableNow; }
    /** Sets runnableNow. */
    public void setRunnableNow(boolean value) { delegate.runnableNow(value); }

    /** Whether the task is left out of the commands and the menu; {@code false} by default. */
    public boolean getHidden() { return delegate.hidden; }
    /** Sets hidden. */
    public void setHidden(boolean value) { delegate.hidden(value); }

    /** Optional conflict-resolution key within the owner scope. */
    @Nullable public String getKey() { return delegate.key; }
    /** Sets key. */
    public void setKey(@Nullable String value) { delegate.key(value); }

    /** Behavior when another live task already owns the key. */
    @NotNull public TaskConflictPolicy getConflictPolicy() { return delegate.conflictPolicy; }
    /** Sets conflictPolicy. */
    public void setConflictPolicy(@NotNull TaskConflictPolicy value) { delegate.conflictPolicy(value); }

    /** Platform execution context used for invocations. */
    @NotNull public TaskExecution getExecution() { return delegate.execution; }
    /** Sets execution. */
    public void setExecution(@NotNull TaskExecution value) { delegate.execution(value); }

    /** Delay before the first invocation. */
    @NotNull public Duration getDelay() { return delegate.delay; }
    /** Sets delay. */
    public void setDelay(@NotNull Duration value) { delegate.delay(value); }

    /** Repeat interval, or {@code null} for a one-shot task. */
    @Nullable public Duration getInterval() { return delegate.interval; }
    /** Sets interval. */
    public void setInterval(@Nullable Duration value) { delegate.interval(value); }

    /** Longest a run may start late before it is skipped; {@code null} never skips. See {@link TaskSpec.Builder#maxLag}. */
    @Nullable public Duration getMaxLag() { return delegate.maxLag; }
    /** Sets maxLag. */
    public void setMaxLag(@Nullable Duration value) { delegate.maxLag(value); }

    /** Failures in a row a repeating task survives; {@code 0} stops it at the first. */
    public int getTolerateFailures() { return delegate.tolerateFailures; }
    /** Sets tolerateFailures. */
    public void setTolerateFailures(int value) { delegate.tolerateFailures(value); }

    /** For a task started by another task: whether it is cancelled together with that task. Off by default. */
    public boolean getCancelWithParent() { return delegate.cancelWithParent; }
    /** Sets cancelWithParent. */
    public void setCancelWithParent(boolean value) { delegate.cancelWithParent(value); }

    /** Application-defined lookup tags. */
    @NotNull public Set<String> getTags() { return new LinkedHashSet<>(delegate.tags); }
    /** Sets the tags. */
    public void setTags(@NotNull Set<String> value) { delegate.tags.clear(); delegate.tags(value); }

    /** Sets the delay before the first invocation in server ticks (50 ms each). */
    public void delayTicks(long value) {
        delegate.delayTicks(value);
    }

    /** Sets the repeat interval in server ticks (50 ms each). */
    public void intervalTicks(long value) {
        delegate.intervalTicks(value);
    }

    /** Reacts when a run starts later than {@code threshold}, for example after the server froze. */
    public void onLag(@NotNull Duration threshold, @NotNull TaskLagHandler handler) {
        delegate.onLag(threshold, handler);
    }

    /** Like {@link #onLag} for a listener that only wants to be told: the late run always goes on. */
    public void whenLate(@NotNull Duration threshold, @NotNull Consumer<TaskLag> listener) {
        delegate.whenLate(threshold, listener);
    }

    /** Adds one lookup tag. */
    public void tag(@NotNull String value) {
        delegate.tag(value);
    }

    /** Adds a predicate that must pass before each invocation runs. */
    public void condition(@NotNull BooleanSupplier test) {
        delegate.condition(test);
    }

    /** Adds a predicate that cancels the task as soon as it passes. */
    public void cancelWhen(@NotNull BooleanSupplier test) {
        delegate.cancelWhen(test);
    }

    /** Sets the callback invoked for each accepted run. */
    public void run(@NotNull TaskAction action) {
        delegate.action(action);
    }
}
