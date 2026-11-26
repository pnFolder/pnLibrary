package org.pnfolder.pnlibrary.spi.tasks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;

import java.time.Duration;
import java.util.Objects;

/** Native scheduling request produced by {@link PlatformTaskBuilder}. */
public final class PlatformTaskRequest {
    private final TaskExecution.Kind executionKind;
    private final Object target;
    private final Duration delay;
    private final Duration interval;
    private final Runnable callback;

    /**
     * Creates a request.
     *
     * @param executionKind requested platform execution context
     * @param target native entity target for entity execution
     * @param delay delay before the first invocation
     * @param interval repeat interval, or {@code null} for one-shot execution
     * @param callback callback dispatched by the platform scheduler
     */
    public PlatformTaskRequest(
            @NotNull TaskExecution.Kind executionKind,
            @Nullable Object target,
            @NotNull Duration delay,
            @Nullable Duration interval,
            @NotNull Runnable callback) {
        if (executionKind == TaskExecution.Kind.ENTITY && target == null) {
            throw new IllegalArgumentException("Entity task target is required");
        }
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Task delay must not be negative");
        }
        if (interval != null && (interval.isNegative() || interval.isZero())) {
            throw new IllegalArgumentException("Task interval must be positive");
        }
        this.executionKind = executionKind;
        this.target = target;
        this.delay = delay;
        this.interval = interval;
        this.callback = callback;
    }

    /** Requested platform execution context. */
    @NotNull
    public TaskExecution.Kind getExecutionKind() {
        return executionKind;
    }

    /** Native entity target for entity execution. */
    @Nullable
    public Object getTarget() {
        return target;
    }

    /** Delay before the first invocation. */
    @NotNull
    public Duration getDelay() {
        return delay;
    }

    /** Repeat interval, or {@code null} for one-shot execution. */
    @Nullable
    public Duration getInterval() {
        return interval;
    }

    /** Callback dispatched by the platform scheduler. */
    @NotNull
    public Runnable getCallback() {
        return callback;
    }

    /** The same request with another {@code callback}. */
    @NotNull
    public PlatformTaskRequest withCallback(@NotNull Runnable callback) {
        return new PlatformTaskRequest(executionKind, target, delay, interval, callback);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlatformTaskRequest)) {
            return false;
        }
        PlatformTaskRequest that = (PlatformTaskRequest) other;
        return executionKind == that.executionKind
                && Objects.equals(target, that.target)
                && delay.equals(that.delay)
                && Objects.equals(interval, that.interval)
                && callback.equals(that.callback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionKind, target, delay, interval, callback);
    }

    @Override
    public String toString() {
        return "PlatformTaskRequest(executionKind=" + executionKind + ", target=" + target + ", delay=" + delay
                + ", interval=" + interval + ", callback=" + callback + ")";
    }
}
