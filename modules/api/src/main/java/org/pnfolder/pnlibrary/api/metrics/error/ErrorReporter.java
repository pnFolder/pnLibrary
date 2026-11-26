package org.pnfolder.pnlibrary.api.metrics.error;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Error reporting contract shared by local diagnostics and remote providers. */
public interface ErrorReporter extends AutoCloseable {
    /** Captures and queues an error; implementations must not throw into application code. */
    void capture(@NotNull TelemetryError error);

    /** Captures a handled {@code throwable} with a bounded stack trace. */
    default void capture(@NotNull Throwable throwable) {
        capture(throwable, null, true, Collections.emptyMap());
    }

    /** Captures a handled {@code throwable} that happened during {@code operation}. */
    default void capture(@NotNull Throwable throwable, @Nullable String operation) {
        capture(throwable, operation, true, Collections.emptyMap());
    }

    /** Convenience conversion for a Throwable with a bounded stack trace. */
    default void capture(@NotNull Throwable throwable, @Nullable String operation, boolean handled,
                         @NotNull Map<String, Object> attributes) {
        List<String> frames = new ArrayList<>();
        for (StackTraceElement frame : throwable.getStackTrace()) {
            frames.add(frame.toString());
        }
        capture(new TelemetryError(throwable.getClass().getName(), throwable.getMessage(), frames, handled, operation, attributes));
    }

    /** Installs a process/class-loader handler when the provider supports it. */
    default void installGlobalHandler() {
    }

    /** Flushes pending reports where supported and releases reporter resources. */
    @Override
    void close();
}
