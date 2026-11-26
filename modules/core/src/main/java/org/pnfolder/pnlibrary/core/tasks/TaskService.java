package org.pnfolder.pnlibrary.core.tasks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;

import java.util.Collections;
import java.util.List;

// Owner identity (not equals) is used for scope lookup: distinct but equal objects get distinct scopes.

/** The library's task scheduler: hands out scopes to owners, and lets administrators look at and stop tasks. */
public interface TaskService extends AutoCloseable {
    /** The scope of {@code owner}, created on first use. */
    @NotNull
    TaskScope scope(@NotNull Object owner);

    /** The scope of {@code owner} named {@code scopeId}. */
    @NotNull
    default TaskScope scope(@NotNull Object owner, @NotNull PluginId scopeId) {
        return scope(owner);
    }

    /** The active task with {@code id}, or {@code null}. */
    @Nullable
    default TaskHandle get(@NotNull TaskId id) {
        return null;
    }

    /** The active task with {@code id}; fails when there is none. */
    @NotNull
    default TaskHandle require(@NotNull TaskId id) {
        TaskHandle handle = get(id);
        if (handle == null) {
            throw new IllegalStateException("Task " + id + " is unavailable");
        }
        return handle;
    }

    /** Every task, active and finished. */
    @NotNull
    default List<TaskSnapshot> query() {
        return query(TaskQuery.all());
    }

    /** The tasks that match {@code query}. */
    @NotNull
    default List<TaskSnapshot> query(@NotNull TaskQuery query) {
        return Collections.emptyList();
    }

    /** Every task of {@code owner}. */
    @NotNull
    default List<TaskSnapshot> query(@NotNull Object owner, @NotNull TaskQuery query) {
        return Collections.emptyList();
    }

    /** Cancels the task with {@code id}; whether it was still active. */
    default boolean cancel(@NotNull TaskId id) {
        TaskHandle handle = get(id);
        return handle != null && handle.cancelIfActive();
    }

    /** Cancels a task as an administrator, if its plugin allows it. */
    @NotNull
    default TaskControl cancelByAdmin(@NotNull TaskId id) {
        return TaskControl.UNAVAILABLE;
    }

    /** Starts a waiting task now, as an administrator, if its plugin allows it. */
    @NotNull
    default TaskControl runNow(@NotNull TaskId id) {
        return TaskControl.UNAVAILABLE;
    }

    /** Cancels every task of {@code owner} and forgets its scopes. */
    void close(@NotNull Object owner);

    @Override
    void close();
}
