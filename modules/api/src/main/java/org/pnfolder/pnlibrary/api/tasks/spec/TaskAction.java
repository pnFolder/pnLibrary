package org.pnfolder.pnlibrary.api.tasks.spec;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext;

/** Action invoked by a scheduled task. */
@FunctionalInterface
public interface TaskAction {
    /** Performs one invocation using its live {@code context}. */
    void run(@NotNull TaskContext context);
}
