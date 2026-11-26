package org.pnfolder.pnlibrary.core.tasks;

import org.jetbrains.annotations.NotNull;

/** Receives the failures of tasks, so the library can put them in its error log. */
@FunctionalInterface
public interface TaskErrorLogger {
    /** Records that a task of {@code owner} failed with {@code error}, described by {@code message}. */
    void log(@NotNull Object owner, @NotNull String message, @NotNull Throwable error);
}
