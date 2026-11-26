package org.pnfolder.pnlibrary.api.tasks;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.spec.ScheduledTask;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

/**
 * Owner-bound group of tasks that are cancelled together.
 *
 * <p>A scope is intended to live for exactly as long as its plugin or subsystem. Calling {@link #close()} cancels
 * every pending and repeating task in the scope and prevents new tasks from being added. Durations must not be
 * negative; repeat intervals must additionally be greater than zero.
 *
 * <pre>{@code
 * TaskScope tasks = context.getTasks();
 * tasks.schedule(TaskSpec.builder().interval(Duration.ofMinutes(1)).action(c -> refreshOnlinePlayers()).build());
 * tasks.close();
 * }</pre>
 */
@ApiStatus.NonExtendable
public interface TaskScope extends AutoCloseable {
    /** Object whose lifecycle owns every task in this scope. */
    @NotNull
    Object getOwner();

    /** Schedules {@code spec} inside this owner-bound scope. */
    @NotNull
    TaskHandle schedule(@NotNull TaskSpec spec);

    /** Schedules a task written as a class; see {@link ScheduledTask}. */
    @NotNull
    default TaskHandle schedule(@NotNull ScheduledTask task) {
        return schedule(task.toSpec());
    }

    /** Returns the task identified by {@code id}, or {@code null} when it is unavailable. */
    @Nullable
    TaskHandle get(@NotNull TaskId id);

    /** Returns the task identified by {@code id} or fails when unavailable. */
    @NotNull
    default TaskHandle require(@NotNull TaskId id) {
        TaskHandle handle = get(id);
        if (handle == null) {
            throw new IllegalStateException("Task " + id + " is unavailable in this scope");
        }
        return handle;
    }

    /** Returns the live task owning {@code key}, or {@code null} when absent. */
    @Nullable
    TaskHandle getByKey(@NotNull String key);

    /** Returns detached snapshots of tasks in this scope that match {@code query}. */
    @NotNull
    List<TaskSnapshot> query(@NotNull TaskQuery query);

    /** Returns detached snapshots of every task in this scope. */
    @NotNull
    default List<TaskSnapshot> query() {
        return query(TaskQuery.all());
    }

    /** Cancels the task identified by {@code id} and reports whether it was active. */
    default boolean cancel(@NotNull TaskId id) {
        TaskHandle handle = get(id);
        return handle != null && handle.cancelIfActive();
    }

    /** Cancels every task and permanently closes this scope. */
    void cancelAll();

    /** Cancels every task and permanently closes this scope. */
    @Override
    default void close() {
        cancelAll();
    }
}
