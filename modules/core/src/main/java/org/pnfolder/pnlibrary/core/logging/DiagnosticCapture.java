package org.pnfolder.pnlibrary.core.logging;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnership;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/** Wraps platform logging: records warnings and errors into the incident buffer and the journal while passing output through. */
public final class DiagnosticCapture implements PlatformLogging {
    private static final long ECHO_WINDOW_NANOS = 2_000_000_000L;
    private static final int ECHO_LIMIT = 512;

    private final PlatformOwnership ownership;
    private final PlatformLogging nativeLogging;
    private final DiagnosticLogBuffer incidents;
    private final JournalSink journal;
    private final ErrorSink onError;
    private final ThreadLocal<Boolean> emitting = new ThreadLocal<>();
    private final ConcurrentHashMap<String, Long> echoes = new ConcurrentHashMap<>();
    private final FailureThrottle stageFailures = new FailureThrottle();

    public DiagnosticCapture(@NotNull PlatformOwnership ownership, @NotNull PlatformLogging nativeLogging,
                             @Nullable DiagnosticLogBuffer incidents, @Nullable JournalSink journal,
                             @NotNull ErrorSink onError) {
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.nativeLogging = Objects.requireNonNull(nativeLogging, "nativeLogging");
        this.incidents = incidents;
        this.journal = journal;
        this.onError = Objects.requireNonNull(onError, "onError");
    }

    public DiagnosticCapture(@NotNull PlatformOwnership ownership, @NotNull PlatformLogging nativeLogging,
                             @Nullable DiagnosticLogBuffer incidents, @Nullable JournalSink journal) {
        this(ownership, nativeLogging, incidents, journal, (owner, message, error) -> { });
    }

    @Override
    public void log(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message, @Nullable Throwable error) {
        write(owner, level, message, error);
    }

    /** Writes to the journal and incident buffer at {@code level} without emitting to the console. */
    public void captureOnly(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message,
                            @Nullable Throwable error) {
        capture(owner, level, message, error, true, Collections.emptyMap());
    }

    public void write(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message,
                      @Nullable Throwable error) {
        write(owner, level, message, error, Collections.emptyMap());
    }

    public void write(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message,
                      @Nullable Throwable error, @NotNull Map<String, ?> context) {
        output(owner, level, message, error, capture(owner, level, message, error, true, context));
    }

    /** Prints the line like {@link #write} but keeps it out of the journal, the error report and the incident buffer. */
    public void writeUnrecorded(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message,
                                @Nullable Throwable error) {
        output(owner, level, message, error, null);
    }

    private void output(Object owner, LogLevel level, String message, Throwable error,
                        DiagnosticLogBuffer.CaptureResult result) {
        if (level == LogLevel.DEBUG) {
            return;
        }
        String text;
        if (result == null || result.isEmitOriginal()) {
            text = message;
        } else if (result.getSummary() != null) {
            text = result.getSummary();
        } else {
            return;
        }
        rememberEcho(level, text);
        emitNative(owner, level, text, text.equals(message) ? error : null);
    }

    @Override
    public void console(@NotNull Object owner, @NotNull String message) {
        emit(() -> nativeLogging.console(owner, message));
    }

    /** Native observers already have console output; never emit it again or capture our own echo. */
    public void observe(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message,
                        @Nullable Throwable error) {
        observeLine(owner, level, message, error, true);
    }

    /** Like {@link #observe} for lines of other plugins: they reach the black box but not the journal. */
    public void observeWithoutJournal(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message,
                                      @Nullable Throwable error) {
        observeLine(owner, level, message, error, false);
    }

    private void observeLine(Object owner, LogLevel level, String message, Throwable error, boolean includeJournal) {
        if (Boolean.TRUE.equals(emitting.get()) || echoedRecently(level, message)) {
            return;
        }
        capture(owner, level, message, error, includeJournal, Collections.emptyMap());
    }

    /** A native logger may echo our line on another thread, where the thread-local guard cannot see it. */
    private boolean echoedRecently(LogLevel level, String message) {
        Long emittedAt = echoes.get(level + "|" + message);
        return emittedAt != null && System.nanoTime() - emittedAt < ECHO_WINDOW_NANOS;
    }

    private void rememberEcho(LogLevel level, String message) {
        long now = System.nanoTime();
        if (echoes.size() > ECHO_LIMIT) {
            echoes.entrySet().removeIf(entry -> now - entry.getValue() >= ECHO_WINDOW_NANOS);
        }
        if (echoes.size() <= ECHO_LIMIT) {
            echoes.put(level + "|" + message, now);
        }
    }

    private DiagnosticLogBuffer.CaptureResult capture(Object owner, LogLevel level, String message, Throwable error,
                                                      boolean includeJournal, Map<String, ?> context) {
        if (includeJournal) {
            guarded(owner, "journal", () -> {
                if (journal != null) {
                    journal.record(owner, level, message, error, context);
                }
                return null;
            });
        }
        // An error logged as text only still has to reach the error tracker, which takes throwables.
        if (level.compareTo(LogLevel.ERROR) >= 0) {
            guarded(owner, "error report", () -> {
                onError.report(owner, message, error != null ? error : new LoggedError(message));
                return null;
            });
        }
        return guarded(owner, "incident buffer",
            () -> incidents == null ? null : incidents.record(ownership, owner, level, message, error));
    }

    /**
     * A broken stage must not stop the line or the other stages, but it must not stay unseen either. The report goes to
     * the console only: recording it would run the broken stage again.
     */
    private <T> T guarded(Object owner, String stage, Callable<T> action) {
        return Failures.runCatchingNonFatal(action).onFailure(error -> reportStageFailure(owner, stage, error))
            .getOrNull();
    }

    private void reportStageFailure(Object owner, String stage, Throwable error) {
        if (!stageFailures.shouldReport(stage)) {
            return;
        }
        String message = "pnLibrary could not pass a log line to the " + stage + ": "
            + error.getClass().getSimpleName() + ": " + error.getMessage();
        rememberEcho(LogLevel.ERROR, message);
        emitNative(owner, LogLevel.ERROR, message, error);
    }

    private void emitNative(Object owner, LogLevel level, String message, Throwable error) {
        emit(() -> nativeLogging.log(owner, level, LogLevelMarks.decorate(level, message), error));
    }

    private void emit(Runnable action) {
        Boolean previous = emitting.get();
        emitting.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            if (previous == null) {
                emitting.remove();
            } else {
                emitting.set(previous);
            }
        }
    }

    /** Stands in for the missing exception of an error logged as plain text. */
    private static final class LoggedError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        LoggedError(String message) {
            super(message);
        }
    }
}
