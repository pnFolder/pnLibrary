package org.pnfolder.pnlibrary.core.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

import java.util.Map;

/** Receives every recorded log line so it can be written to the journal. */
@FunctionalInterface
public interface JournalSink {
    void record(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message, @Nullable Throwable error,
                @NotNull Map<String, ?> context);
}
