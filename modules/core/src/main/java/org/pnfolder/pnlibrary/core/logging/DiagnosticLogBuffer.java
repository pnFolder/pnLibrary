package org.pnfolder.pnlibrary.core.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticRedactor;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticThrowables;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Bounded in-memory buffer of logged incidents that collapses repeated occurrences of the same problem.
 *
 * <p>The redaction and the fingerprint are the expensive steps (a dozen regexes, SHA-256), so they run only the first
 * time a raw combination of plugin, level, text and error shape is seen; a repeat is found by that raw key alone.
 */
public final class DiagnosticLogBuffer {
    private static final int MAX_MESSAGE_CHARS = 4_096;
    private static final int RAW_KEYS_LIMIT = 8_192;
    private static final int MAX_ERROR_CHARS = 1_048_576;
    private static final int MAX_TIMELINE_OCCURRENCES = 100_000;
    private static final long SUMMARY_INTERVAL_MS = 5 * 60 * 1_000L;

    /** The decision for one record: whether the original line is printed, or a short summary instead. */
    public static final class CaptureResult {
        private final boolean emitOriginal;
        private final String summary;

        CaptureResult(boolean emitOriginal, @Nullable String summary) {
            this.emitOriginal = emitOriginal;
            this.summary = summary;
        }

        public boolean isEmitOriginal() {
            return emitOriginal;
        }

        public @Nullable String getSummary() {
            return summary;
        }
    }

    private final int capacity;
    private final Function<String, String> redact;
    private final IncidentFingerprint fingerprintOf;
    private final Map<String, LoggedIncident> incidents = new LinkedHashMap<String, LoggedIncident>();
    private final DiagnosticRedactor redactor = new DiagnosticRedactor();
    private final Map<RawIncidentKey, String> incidentOfRaw = new LinkedHashMap<RawIncidentKey, String>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<RawIncidentKey, String> eldest) {
            return size() > RAW_KEYS_LIMIT;
        }
    };
    private int timelineEntryCount;
    private volatile Consumer<List<Map<String, Object>>> changeListener;

    public DiagnosticLogBuffer() {
        this(2_000);
    }

    public DiagnosticLogBuffer(int capacity) {
        this(capacity, new DiagnosticRedactor()::redact,
            (context, message, error) -> DiagnosticThrowables.fingerprint(error, context, message));
    }

    public DiagnosticLogBuffer(int capacity, @NotNull Function<String, String> redact,
                               @NotNull IncidentFingerprint fingerprintOf) {
        this.capacity = capacity;
        this.redact = redact;
        this.fingerprintOf = fingerprintOf;
    }

    public void onChange(@NotNull Consumer<List<Map<String, Object>>> listener) {
        changeListener = listener;
    }

    /**
     * Records one occurrence. The first occurrence keeps the complete throwable; repeats only add their exact time.
     * The returned decision prevents identical stack traces from flooding the native console.
     */
    public synchronized @Nullable CaptureResult record(@NotNull PlatformOwnership platform, @NotNull Object owner,
                                                       @NotNull LogLevel level, @NotNull String message,
                                                       @Nullable Throwable error) {
        if (level.compareTo(LogLevel.WARNING) < 0) {
            return null;
        }
        long nowMs = System.currentTimeMillis();
        String now = Instant.ofEpochMilli(nowMs).toString();
        String rawPlugin = platform.ownerDetails(owner).get("name");
        if (rawPlugin == null) {
            rawPlugin = owner.getClass().getSimpleName();
        }
        RawIncidentKey rawKey = rawKeyOf(rawPlugin, level, message, error);
        String knownId = incidentOfRaw.get(rawKey);
        LoggedIncident known = knownId == null ? null : incidents.get(knownId);
        if (known != null) {
            return repeatIncident(known, known.plugin, nowMs, now);
        }
        String plugin = take(redact.apply(rawPlugin), 96);
        String safeMessage = take(redact.apply(message), MAX_MESSAGE_CHARS);
        String fingerprint = fingerprintOf.of(Arrays.asList(plugin, level.name()), safeMessage, error);
        incidentOfRaw.put(rawKey, fingerprint);
        LoggedIncident existing = incidents.get(fingerprint);
        if (existing == null) {
            return startIncident(fingerprint, level, plugin, safeMessage, error, nowMs, now);
        }
        return repeatIncident(existing, plugin, nowMs, now);
    }

    private static RawIncidentKey rawKeyOf(String plugin, LogLevel level, String message, Throwable error) {
        Throwable significant = DiagnosticThrowables.significantCause(error);
        StackTraceElement frame = DiagnosticThrowables.originFrame(error);
        return new RawIncidentKey(plugin, level, message,
            significant == null ? null : significant.getClass().getName(),
            significant == null ? null : significant.getMessage(),
            frame == null ? null : frame.getClassName(), frame == null ? null : frame.getMethodName());
    }

    private CaptureResult startIncident(String fingerprint, LogLevel level, String plugin, String message,
                                        Throwable error, long nowMs, String now) {
        String completeError = error == null ? null : fullStackTrace(error);
        LoggedIncident incident = new LoggedIncident(fingerprint, level, plugin, message,
            error == null ? null : error.getClass().getName(),
            error == null ? null : DiagnosticThrowables.originData(error), completeError,
            consoleBlock(now, level, message, completeError), now, nowMs);
        addOccurrence(incident, now);
        incidents.put(fingerprint, incident);
        while (incidents.size() > Math.max(capacity, 1)) {
            Iterator<LoggedIncident> oldest = incidents.values().iterator();
            timelineEntryCount -= oldest.next().occurrences.size();
            oldest.remove();
        }
        notifyChanged();
        return new CaptureResult(true, null);
    }

    /** A repeat is quiet except at milestones and once per interval, so a loop cannot flood the console. */
    private CaptureResult repeatIncident(LoggedIncident existing, String plugin, long nowMs, String now) {
        existing.count++;
        existing.lastSeenUtc = now;
        addOccurrence(existing, now);
        notifyChanged();
        boolean milestone = existing.count == 10L || existing.count == 100L || existing.count % 1_000L == 0L;
        boolean intervalElapsed = nowMs - existing.lastSummaryAtMs >= SUMMARY_INTERVAL_MS;
        if (!milestone && !intervalElapsed) {
            return new CaptureResult(false, null);
        }
        existing.lastSummaryAtMs = nowMs;
        return new CaptureResult(false, "[" + plugin + "] Repeated incident " + existing.id + ": " + existing.message
            + " (" + existing.count + " occurrences, " + existing.firstSeenUtc + " — " + existing.lastSeenUtc + ")");
    }

    public synchronized int expireOlderThan(long cutoffMs) {
        int expired = 0;
        Iterator<LoggedIncident> iterator = incidents.values().iterator();
        while (iterator.hasNext()) {
            LoggedIncident incident = iterator.next();
            if (Instant.parse(incident.lastSeenUtc).toEpochMilli() < cutoffMs) {
                iterator.remove();
                timelineEntryCount -= incident.occurrences.size();
                expired++;
            }
        }
        if (expired > 0) {
            notifyChanged();
        }
        return expired;
    }

    public synchronized @NotNull List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (LoggedIncident incident : incidents.values()) {
            result.add(incident.toMap());
        }
        return result;
    }

    public synchronized @NotNull Map<String, Object> summary() {
        return IncidentSummary.of(incidents.values());
    }

    private void notifyChanged() {
        Consumer<List<Map<String, Object>>> listener = changeListener;
        if (listener != null) {
            Failures.runCatchingNonFatal(() -> {
                listener.accept(snapshot());
                return null;
            });
        }
    }

    private String fullStackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        String redacted = redactor.redactStackTrace(writer.toString());
        if (redacted.length() <= MAX_ERROR_CHARS) {
            return redacted;
        }
        return redacted.substring(0, MAX_ERROR_CHARS)
            + "\n[TRUNCATED: throwable exceeded the hard " + MAX_ERROR_CHARS + "-character safety limit]";
    }

    private void addOccurrence(LoggedIncident incident, String timeUtc) {
        if (timelineEntryCount < MAX_TIMELINE_OCCURRENCES) {
            incident.occurrences.addLast(new IncidentOccurrence(timeUtc, Thread.currentThread().getName()));
            timelineEntryCount++;
        } else {
            incident.omittedOccurrences++;
        }
    }

    private static String consoleBlock(String timeUtc, LogLevel level, String message, String fullError) {
        StringBuilder block = new StringBuilder()
            .append('[').append(timeUtc).append(' ').append(level.name()).append("] ").append(message);
        if (fullError != null) {
            block.append('\n').append(fullError);
        }
        return block.toString();
    }

    private static String take(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
