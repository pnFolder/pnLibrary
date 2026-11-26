package org.pnfolder.pnlibrary.core.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.error.TelemetryError;
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Local error pipeline that sanitizes and deduplicates events before forwarding them.
 * Remote providers are deliberately optional; local capture remains available when they are off.
 */
public final class ErrorPipeline implements ErrorReporter {
    private static final int DEFAULT_MAX_STACK_FRAMES = 80;
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern QUERY_SECRET = Pattern.compile("(?i)([?&](?:api_?key|token|secret)=)[^&\\s]+");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HOME_FOLDER = Pattern.compile("(?i)(/home/|/Users/|C:\\\\Users\\\\)[^/\\\\\\s]+");

    private final ErrorReporter delegate;
    private final int maxStackFrames;
    private final Consumer<Throwable> onForwardingFailure;
    private final FailureThrottle failureThrottle = new FailureThrottle();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final AtomicLong captured = new AtomicLong();
    private final AtomicLong deduplicated = new AtomicLong();
    private final AtomicLong forwarded = new AtomicLong();
    private final AtomicLong forwardingFailures = new AtomicLong();

    /** A pipeline that only sanitizes and deduplicates; nothing is forwarded. */
    public ErrorPipeline() {
        this(null);
    }

    /** A pipeline that forwards to {@code delegate}, with the default stack bound and no failure callback. */
    public ErrorPipeline(@Nullable ErrorReporter delegate) {
        this(delegate, DEFAULT_MAX_STACK_FRAMES, failure -> { });
    }

    /** A pipeline with the default stack bound that reports forwarding failures to {@code onForwardingFailure}. */
    public ErrorPipeline(@Nullable ErrorReporter delegate, @NotNull Consumer<Throwable> onForwardingFailure) {
        this(delegate, DEFAULT_MAX_STACK_FRAMES, onForwardingFailure);
    }

    /**
     * Creates a pipeline.
     *
     * @param delegate remote reporter, or {@code null} to keep errors local
     * @param maxStackFrames how many stack frames of one error are kept
     * @param onForwardingFailure told once per failure type when {@code delegate} throws
     */
    public ErrorPipeline(@Nullable ErrorReporter delegate, int maxStackFrames,
                         @NotNull Consumer<Throwable> onForwardingFailure) {
        this.delegate = delegate;
        this.maxStackFrames = maxStackFrames;
        this.onForwardingFailure = onForwardingFailure;
    }

    @Override
    public void capture(@NotNull TelemetryError error) {
        if (closed.get()) {
            return;
        }
        captured.incrementAndGet();
        TelemetryError safe = sanitize(error);
        if (!seen.add(fingerprint(safe))) {
            deduplicated.incrementAndGet();
            return;
        }
        forwarded.incrementAndGet();
        Failures.runCatchingNonFatal(() -> {
            if (delegate != null) {
                delegate.capture(safe);
            }
            return null;
        }).onFailure(this::forwardingFailed);
    }

    @Override
    public void installGlobalHandler() {
        if (delegate != null) {
            delegate.installGlobalHandler();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            Failures.runCatchingNonFatal(() -> {
                if (delegate != null) {
                    delegate.close();
                }
                return null;
            });
        }
    }

    /** Counters of this pipeline for the diagnostics report. */
    @NotNull
    public Map<String, Object> diagnosticSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("captured", captured.get());
        snapshot.put("deduplicated", deduplicated.get());
        snapshot.put("forwarded", forwarded.get());
        snapshot.put("forwardingFailures", forwardingFailures.get());
        snapshot.put("uniqueFingerprints", seen.size());
        snapshot.put("closed", closed.get());
        return snapshot;
    }

    private void forwardingFailed(Throwable failure) {
        forwardingFailures.incrementAndGet();
        if (failureThrottle.shouldReport(failure.getClass().getName())) {
            onForwardingFailure.accept(failure);
        }
    }

    private static String fingerprint(TelemetryError error) {
        return error.getType() + "|" + error.getMessage() + "|" + String.join("\n", error.getStackTrace())
                + "|" + error.getOperation();
    }

    private TelemetryError sanitize(TelemetryError error) {
        List<String> frames = new ArrayList<>();
        for (String frame : error.getStackTrace()) {
            if (frames.size() >= maxStackFrames) {
                break;
            }
            frames.add(redact(frame));
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : error.getAttributes().entrySet()) {
            Object value = entry.getValue();
            attributes.put(entry.getKey(), redact(value == null ? null : value.toString()));
        }
        return new TelemetryError(error.getType(), redact(error.getMessage()), frames, error.isHandled(),
                error.getOperation(), attributes);
    }

    @Nullable
    private static String redact(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String text = BEARER.matcher(value).replaceAll("$1[redacted]");
        text = QUERY_SECRET.matcher(text).replaceAll("$1[redacted]");
        text = UUID.matcher(text).replaceAll("[uuid]");
        return HOME_FOLDER.matcher(text).replaceAll("$1[user]");
    }
}
