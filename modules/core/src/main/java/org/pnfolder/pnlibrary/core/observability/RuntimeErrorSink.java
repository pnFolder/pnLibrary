package org.pnfolder.pnlibrary.core.observability;

import org.jetbrains.annotations.NotNull;

/** Told about an error that the library itself met while handling a log line. */
@FunctionalInterface
public interface RuntimeErrorSink {
    void accept(@NotNull Object owner, @NotNull String message, @NotNull Throwable error);
}
