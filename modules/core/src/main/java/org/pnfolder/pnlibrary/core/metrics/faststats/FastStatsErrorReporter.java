package org.pnfolder.pnlibrary.core.metrics.faststats;

import dev.faststats.ErrorTracker;
import dev.faststats.TrackedError;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.error.TelemetryError;
import org.pnfolder.pnlibrary.internal.faststats.FastStatsBridge;

import java.util.List;
import java.util.Map;

/** Sends pnLibrary telemetry errors to the FastStats tracker. */
public final class FastStatsErrorReporter implements ErrorReporter {
    private final ErrorTracker tracker = FastStatsBridge.errorTracker();

    /** The FastStats tracker errors are sent to. */
    @NotNull
    public ErrorTracker getTracker() {
        return tracker;
    }

    @Override
    public void capture(@NotNull TelemetryError error) {
        RuntimeException throwable = new RuntimeException(error.getType() + ": " + error.getMessage());
        List<String> frames = error.getStackTrace();
        StackTraceElement[] elements = new StackTraceElement[frames.size()];
        for (int index = 0; index < elements.length; index++) {
            elements[index] = new StackTraceElement("reported", frames.get(index), null, -1);
        }
        throwable.setStackTrace(elements);
        TrackedError tracked = tracker.trackError(throwable);
        tracked.handled(error.isHandled());
        if (error.getOperation() != null) {
            tracked.attributes().put("operation", error.getOperation());
        }
        for (Map.Entry<String, Object> entry : error.getAttributes().entrySet()) {
            if (entry.getValue() != null) {
                tracked.attributes().put(entry.getKey(), entry.getValue().toString());
            }
        }
    }

    /** Nothing to release: the FastStats tracker lives for the whole process. */
    @Override
    public void close() {
    }
}
