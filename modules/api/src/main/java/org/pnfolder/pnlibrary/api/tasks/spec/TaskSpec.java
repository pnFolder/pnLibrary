package org.pnfolder.pnlibrary.api.tasks.spec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.info.TaskLag;
import org.pnfolder.pnlibrary.api.tasks.run.LagResponse;
import org.pnfolder.pnlibrary.api.tasks.run.TaskLagHandler;

/** Immutable declaration used to schedule one task; create it with {@link #builder()}. */
public final class TaskSpec {
    private static final long MILLIS_PER_TICK = 50L;
    private static final Duration DEFAULT_LAG_THRESHOLD = Duration.ofSeconds(2);

    private final String name;
    private final String description;
    private final String usage;
    private final boolean cancellable;
    private final boolean runnableNow;
    private final boolean hidden;
    private final String key;
    private final TaskConflictPolicy conflictPolicy;
    private final TaskExecution execution;
    private final Duration delay;
    private final Duration interval;
    private final Duration maxLag;
    private final int tolerateFailures;
    private final Duration lagThreshold;
    private final TaskLagHandler lagHandler;
    private final boolean cancelWithParent;
    private final List<BooleanSupplier> conditions;
    private final List<BooleanSupplier> cancellationConditions;
    private final Set<String> tags;
    private final TaskAction action;

    private TaskSpec(Builder b) {
        name = b.name;
        description = b.description == null || b.description.trim().isEmpty() ? null : b.description;
        usage = b.usage == null || b.usage.trim().isEmpty() ? null : b.usage;
        cancellable = b.cancellable;
        runnableNow = b.runnableNow;
        hidden = b.hidden;
        key = b.key;
        conflictPolicy = b.conflictPolicy;
        execution = b.execution;
        delay = b.delay;
        interval = b.interval;
        maxLag = b.maxLag;
        tolerateFailures = b.tolerateFailures;
        lagThreshold = b.lagThreshold;
        lagHandler = b.lagHandler;
        cancelWithParent = b.cancelWithParent;
        conditions = Collections.unmodifiableList(new ArrayList<>(b.conditions));
        cancellationConditions = Collections.unmodifiableList(new ArrayList<>(b.cancellationConditions));
        tags = Collections.unmodifiableSet(new LinkedHashSet<>(b.tags));
        action = b.action;
    }

    /** Creates a new {@link Builder}. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Optional human-readable task name. */
    @Nullable public String getName() { return name; }
    /** Optional sentence on what the task is for, shown by {@code /pn tasks} and the menu. */
    @Nullable public String getDescription() { return description; }
    /** Optional note on how the task is used or what it changes. */
    @Nullable public String getUsage() { return usage; }
    /** Whether an administrator may stop the task; the owner can always cancel it. */
    public boolean getCancellable() { return cancellable; }
    /** Whether an administrator may start a waiting repeating task at once. */
    public boolean getRunnableNow() { return runnableNow; }
    /** Whether the task is left out of {@code /pn tasks} and the menu. */
    public boolean getHidden() { return hidden; }
    /** Optional conflict-resolution key within an owner scope. */
    @Nullable public String getKey() { return key; }
    /** Behavior when {@link #getKey() key} is already registered. */
    @NotNull public TaskConflictPolicy getConflictPolicy() { return conflictPolicy; }
    /** Platform execution context. */
    @NotNull public TaskExecution getExecution() { return execution; }
    /** Delay before the first invocation. */
    @NotNull public Duration getDelay() { return delay; }
    /** Repeat interval, or {@code null} for a one-shot task. */
    @Nullable public Duration getInterval() { return interval; }
    /** Longest a run may start late before it is skipped instead; {@code null} runs it anyway. */
    @Nullable public Duration getMaxLag() { return maxLag; }
    /** How many failures in a row a repeating task survives before it is stopped as failed. */
    public int getTolerateFailures() { return tolerateFailures; }
    /** How late a run may start before the {@link #getLagHandler() lag handler} is asked about it. */
    @NotNull public Duration getLagThreshold() { return lagThreshold; }
    /** Reaction to a run that starts later than the threshold, or {@code null} for none. */
    @Nullable public TaskLagHandler getLagHandler() { return lagHandler; }
    /** For a task started by another task: whether it is cancelled when that task is cancelled. */
    public boolean getCancelWithParent() { return cancelWithParent; }
    /** Predicates that must all pass before an invocation runs. */
    @NotNull public List<BooleanSupplier> getConditions() { return conditions; }
    /** Predicates that cancel the task when any passes. */
    @NotNull public List<BooleanSupplier> getCancellationConditions() { return cancellationConditions; }
    /** Immutable application-defined lookup tags. */
    @NotNull public Set<String> getTags() { return tags; }
    /** Callback invoked for each accepted run. */
    @NotNull public TaskAction getAction() { return action; }

    /** Fluent builder for a validated {@link TaskSpec}. */
    public static final class Builder {
        String name;
        String description;
        String usage;
        boolean cancellable = true;
        boolean runnableNow = true;
        boolean hidden;
        String key;
        TaskConflictPolicy conflictPolicy = TaskConflictPolicy.REJECT;
        TaskExecution execution = TaskExecution.global();
        Duration delay = Duration.ZERO;
        Duration interval;
        Duration maxLag;
        int tolerateFailures;
        Duration lagThreshold = DEFAULT_LAG_THRESHOLD;
        TaskLagHandler lagHandler;
        boolean cancelWithParent;
        final List<BooleanSupplier> conditions = new ArrayList<>();
        final List<BooleanSupplier> cancellationConditions = new ArrayList<>();
        final Set<String> tags = new LinkedHashSet<>();
        TaskAction action;

        private Builder() {}

        /** Sets the optional human-readable task name. */
        @NotNull public Builder name(@Nullable String value) { name = value; return this; }
        /** Sets the optional sentence on what the task is for. */
        @NotNull public Builder description(@Nullable String value) { description = value; return this; }
        /** Sets the optional note on how the task is used or what it changes. */
        @NotNull public Builder usage(@Nullable String value) { usage = value; return this; }
        /** Sets whether an administrator may stop the task from the commands and the menu; {@code true} by default. */
        @NotNull public Builder cancellable(boolean value) { cancellable = value; return this; }
        /** Sets whether an administrator may start the task at once; {@code true} by default. */
        @NotNull public Builder runnableNow(boolean value) { runnableNow = value; return this; }
        /** Sets whether the task is left out of the commands and the menu; {@code false} by default. */
        @NotNull public Builder hidden(boolean value) { hidden = value; return this; }
        /** Sets the conflict-resolution key within the owner scope; {@code null} means no key. */
        @NotNull public Builder key(@Nullable String value) { key = value; return this; }
        /** Sets the behavior when another live task already owns the key. */
        @NotNull public Builder conflictPolicy(@NotNull TaskConflictPolicy value) { conflictPolicy = value; return this; }
        /** Selects the platform execution context. */
        @NotNull public Builder execution(@NotNull TaskExecution value) { execution = value; return this; }
        /** Sets the delay before the first invocation. */
        @NotNull public Builder delay(@NotNull Duration value) { delay = value; return this; }
        /** Sets the delay before the first invocation in server ticks; one tick is 50 milliseconds. */
        @NotNull public Builder delayTicks(long value) { delay = ticksToDuration(value, "delay"); return this; }
        /** Sets the repeat interval; {@code null} makes the task one-shot. */
        @NotNull public Builder interval(@Nullable Duration value) { interval = value; return this; }
        /** Sets a repeat interval in server ticks; one tick is 50 milliseconds. */
        @NotNull public Builder intervalTicks(long value) { interval = ticksToDuration(value, "interval"); return this; }

        /**
         * Skips a run that starts more than {@code value} after its planned time, such as after the server froze,
         * instead of running it late. A skipped run counts as skipped; a one-shot task that is too late simply ends.
         * {@code null} (the default) never skips.
         */
        @NotNull
        public Builder maxLag(@Nullable Duration value) {
            maxLag = value;
            return this;
        }

        /**
         * Lets a repeating task survive up to {@code value} failures in a row: each is logged and the task tries
         * again at the next interval. A successful run resets the count. {@code 0} (the default) stops the task at
         * the first failure.
         */
        @NotNull
        public Builder tolerateFailures(int value) {
            tolerateFailures = value;
            return this;
        }

        /**
         * Calls {@code handler} right before a run that starts more than {@code threshold} after its planned time,
         * such as after the server froze, and lets it run or skip that run. Without a handler late runs simply run
         * late.
         */
        @NotNull
        public Builder onLag(@NotNull Duration threshold, @NotNull TaskLagHandler handler) {
            lagThreshold = threshold;
            lagHandler = handler;
            return this;
        }

        /** Like {@link #onLag} for a handler that only wants to be told: the late run always goes on. */
        @NotNull
        public Builder whenLate(@NotNull Duration threshold, @NotNull final Consumer<TaskLag> listener) {
            return onLag(threshold, lag -> {
                listener.accept(lag);
                return LagResponse.RUN;
            });
        }

        /** For a task started by another task: cancels it together with that task. Off by default. */
        @NotNull public Builder cancelWithParent(boolean value) { cancelWithParent = value; return this; }
        /** Adds a predicate that must pass before each invocation runs. */
        @NotNull public Builder condition(@NotNull BooleanSupplier value) { conditions.add(value); return this; }
        /** Adds a predicate that cancels the task as soon as it passes. */
        @NotNull public Builder cancelWhen(@NotNull BooleanSupplier value) { cancellationConditions.add(value); return this; }
        /** Adds one lookup tag. */
        @NotNull public Builder tag(@NotNull String value) { tags.add(value); return this; }
        /** Adds several lookup tags. */
        @NotNull public Builder tags(@NotNull java.util.Collection<String> values) { tags.addAll(values); return this; }
        /** Sets the required callback invoked for each accepted run. */
        @NotNull public Builder action(@NotNull TaskAction value) { action = value; return this; }

        /** Validates this declaration and creates an immutable task specification. */
        @NotNull
        public TaskSpec build() {
            require(!delay.isNegative(), "Task delay must not be negative");
            require(interval == null || !interval.isNegative() && !interval.isZero(), "Task interval must be positive");
            require(key == null || !key.trim().isEmpty(), "Task key must not be blank");
            require(maxLag == null || !maxLag.isNegative() && !maxLag.isZero(), "Task max lag must be positive");
            require(tolerateFailures >= 0, "Tolerated failures must not be negative");
            require(!lagThreshold.isNegative(), "Task lag threshold must not be negative");
            for (String tag : tags) {
                require(!tag.trim().isEmpty(), "Task tags must not be blank");
            }
            if (action == null) {
                throw new IllegalStateException("Task action is required");
            }
            return new TaskSpec(this);
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalArgumentException(message);
            }
        }

        private static Duration ticksToDuration(long value, String label) {
            require(value >= 0, "Task " + label + " ticks must not be negative");
            return Duration.ofMillis(Math.multiplyExact(value, MILLIS_PER_TICK));
        }
    }
}
