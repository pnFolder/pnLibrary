package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

/** Receives one log line with the plugin or platform object it belongs to. */
@FunctionalInterface
public interface PlatformLogSink {
    /** Handles {@code message} written at {@code level} for {@code owner}, with the {@code error} that explains it, if any. */
    void log(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message, @Nullable Throwable error);
}
