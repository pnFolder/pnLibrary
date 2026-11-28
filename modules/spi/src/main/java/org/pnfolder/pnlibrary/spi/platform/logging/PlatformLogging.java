package org.pnfolder.pnlibrary.spi.platform.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

/** Native logging boundary used by shared runtime services. */
public interface PlatformLogging {
    /** Sink for log lines; the default prints to the process console. */
    @NotNull
    default PlatformLogSink getLogHandler() {
        return (owner, level, message, error) -> {
            String line = "[pnLibrary/" + level.name() + "] " + message;
            if (level.compareTo(LogLevel.ERROR) >= 0) {
                System.err.println(line);
            } else {
                System.out.println(line);
            }
            if (error != null) {
                error.printStackTrace(System.err);
            }
        };
    }

    /** Logs {@code message} for {@code owner} at {@code level}. */
    default void log(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message) {
        log(owner, level, message, null);
    }

    /** Logs {@code message} for {@code owner} at {@code level}, attaching {@code error} when present. */
    default void log(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message, @Nullable Throwable error) {
        getLogHandler().log(owner, level, message, error);
    }

    /** Prints {@code message} to the server console at info level. */
    default void console(@NotNull Object owner, @NotNull String message) {
        log(owner, LogLevel.INFO, message);
    }
}
