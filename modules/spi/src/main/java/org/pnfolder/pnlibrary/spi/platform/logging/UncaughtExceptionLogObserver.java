package org.pnfolder.pnlibrary.spi.platform.logging;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

/**
 * Observes uncaught process exceptions, preserving the handler installed before this bridge.
 *
 * <p>{@code owner} identifies the observer, not necessarily the plugin that caused the exception.
 * This does not intercept SLF4J output or exceptions already handled by native schedulers.
 */
public final class UncaughtExceptionLogObserver {
    private final Object owner;
    private volatile PlatformLogSink callback;
    private Thread.UncaughtExceptionHandler previous;
    private Thread.UncaughtExceptionHandler installed;

    public UncaughtExceptionLogObserver(@NotNull Object owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /** Installs or removes the uncaught-exception hook; {@code null} restores the previous handler. */
    public synchronized void observe(@Nullable PlatformLogSink observer) {
        callback = observer;
        if (observer != null && installed == null) {
            previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.UncaughtExceptionHandler predecessor = previous;
            installed = (thread, error) -> report(predecessor, thread, error);
            Thread.setDefaultUncaughtExceptionHandler(installed);
        } else if (observer == null && installed != null) {
            if (Thread.getDefaultUncaughtExceptionHandler() == installed) {
                Thread.setDefaultUncaughtExceptionHandler(previous);
            }
            previous = null;
            installed = null;
        }
    }

    private void report(Thread.UncaughtExceptionHandler predecessor, Thread thread, Throwable error) {
        try {
            PlatformLogSink sink = callback;
            if (sink != null) {
                sink.log(owner, LogLevel.ERROR, "Uncaught process exception on thread " + thread.getName(), error);
            }
        } finally {
            if (predecessor != null) {
                predecessor.uncaughtException(thread, error);
            } else {
                error.printStackTrace();
            }
        }
    }
}
