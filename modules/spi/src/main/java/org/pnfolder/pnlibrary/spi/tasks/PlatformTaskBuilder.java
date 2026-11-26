package org.pnfolder.pnlibrary.spi.tasks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;

import java.time.Duration;
import java.util.function.Function;

/**
 * Fluent description of a task, started with {@link PlatformTaskAdapter#buildTask()}.
 *
 * <p>Unless changed the task is global, starts immediately and runs once:
 *
 * <pre>{@code
 * adapter.buildTask()
 *         .task(callback)
 *         .delay(Duration.ofSeconds(5))
 *         .every(Duration.ofSeconds(30))
 *         .schedule();
 * }</pre>
 */
public final class PlatformTaskBuilder {
    private final Function<PlatformTaskRequest, PlatformTaskHandle> dispatch;
    private Runnable callback;
    private TaskExecution.Kind kind = TaskExecution.Kind.GLOBAL;
    private Object target;
    private Duration delay = Duration.ZERO;
    private Duration interval;

    /** @param dispatch hands the finished request to the platform scheduler */
    public PlatformTaskBuilder(@NotNull Function<PlatformTaskRequest, PlatformTaskHandle> dispatch) {
        this.dispatch = dispatch;
    }

    /** Sets the {@code callback} the task runs; required. */
    @NotNull
    public PlatformTaskBuilder task(@NotNull Runnable callback) {
        this.callback = callback;
        return this;
    }

    /** Runs the task in the execution context of the entity {@code target}. */
    @NotNull
    public PlatformTaskBuilder entity(@NotNull Object target) {
        return execution(TaskExecution.Kind.ENTITY, target);
    }

    /** Runs the task in the given execution {@code kind}, with no target. */
    @NotNull
    public PlatformTaskBuilder execution(@NotNull TaskExecution.Kind kind) {
        return execution(kind, null);
    }

    /** Runs the task in the given execution {@code kind}, bound to {@code target} when the kind needs one. */
    @NotNull
    public PlatformTaskBuilder execution(@NotNull TaskExecution.Kind kind, @Nullable Object target) {
        this.kind = kind;
        this.target = target;
        return this;
    }

    /** Waits {@code delay} before the first run. */
    @NotNull
    public PlatformTaskBuilder delay(@NotNull Duration delay) {
        this.delay = delay;
        return this;
    }

    /** Repeats the task every {@code interval}. */
    @NotNull
    public PlatformTaskBuilder every(@NotNull Duration interval) {
        this.interval = interval;
        return this;
    }

    /** Copies every setting from an already built {@code request}. */
    @NotNull
    public PlatformTaskBuilder from(@NotNull PlatformTaskRequest request) {
        callback = request.getCallback();
        kind = request.getExecutionKind();
        target = request.getTarget();
        delay = request.getDelay();
        interval = request.getInterval();
        return this;
    }

    /** Builds the request without scheduling it. */
    @NotNull
    public PlatformTaskRequest build() {
        if (callback == null) {
            throw new IllegalStateException("Task callback is not set");
        }
        return new PlatformTaskRequest(kind, target, delay, interval, callback);
    }

    /** Schedules the task and returns its native cancellation handle. */
    @NotNull
    public PlatformTaskHandle schedule() {
        return dispatch.apply(build());
    }
}
