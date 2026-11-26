package org.pnfolder.pnlibrary.core.logging;

import org.jetbrains.annotations.NotNull;

/** Receives every logged error so it can reach the error tracker. */
@FunctionalInterface
public interface ErrorSink {
    void report(@NotNull Object owner, @NotNull String message, @NotNull Throwable error);
}
