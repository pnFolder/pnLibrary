package org.pnfolder.pnlibrary.core.plugin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.core.tasks.TaskService;

/** A task service that always hands out one scope. */
public final class TaskServiceRecorder implements TaskService {
    private final TaskScopeRecorder scope;

    public TaskServiceRecorder(TaskScopeRecorder scope) {
        this.scope = scope;
    }

    @Override
    public @NotNull TaskScope scope(@NotNull Object owner) {
        return scope.value;
    }

    @Override
    public void close(@NotNull Object owner) {
        scope.value.close();
    }

    @Override
    public void close() {
        scope.value.close();
    }
}
