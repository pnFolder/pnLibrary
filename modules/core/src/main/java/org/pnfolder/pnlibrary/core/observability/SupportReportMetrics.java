package org.pnfolder.pnlibrary.core.observability;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Counts how support reports were generated: successes, failures, durations and sizes. */
final class SupportReportMetrics {
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final AtomicLong created = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong durationTotalMs = new AtomicLong();
    private final AtomicLong sizeTotalBytes = new AtomicLong();
    private final Map<String, AtomicLong> failuresByType = new ConcurrentHashMap<>();
    private volatile String lastCreatedUtc;
    private volatile Long lastDurationMs;
    private volatile Long lastSizeBytes;

    boolean tryStart() {
        return inProgress.compareAndSet(false, true);
    }

    void finish() {
        inProgress.set(false);
    }

    void succeeded(long startedAtNanos, Path report) {
        created.incrementAndGet();
        lastCreatedUtc = Instant.now().toString();
        lastDurationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
        lastSizeBytes = Failures.runCatchingNonFatal(() -> Files.size(report)).getOrNull();
        durationTotalMs.addAndGet(lastDurationMs);
        sizeTotalBytes.addAndGet(lastSizeBytes == null ? 0L : lastSizeBytes);
    }

    void failed(long startedAtNanos, Throwable error) {
        failed.incrementAndGet();
        failuresByType.computeIfAbsent(error.getClass().getName(), key -> new AtomicLong()).incrementAndGet();
        lastDurationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    @NotNull
    Map<String, Object> summary(List<Map.Entry<String, byte[]>> historyFiles, Map<String, Object> uploadLedger,
                                int maxReportBytes) {
        long ok = created.get();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("inProgress", inProgress.get());
        summary.put("created", ok);
        summary.put("failed", failed.get());
        summary.put("total", ok + failed.get());
        summary.put("successRate", (double) ok / Math.max(ok + failed.get(), 1L));
        summary.put("averageDurationMs", (double) durationTotalMs.get() / Math.max(ok, 1L));
        summary.put("averageSizeBytes", (double) sizeTotalBytes.get() / Math.max(ok, 1L));
        summary.put("failuresByType", sortedFailures());
        summary.put("lastCreatedUtc", lastCreatedUtc);
        summary.put("lastDurationMs", lastDurationMs);
        summary.put("lastSizeBytes", lastSizeBytes);
        summary.put("maxReportBytes", maxReportBytes);
        Long size = lastSizeBytes;
        summary.put("lastSizeRatio", size == null ? null : (Double) (size / Math.max((double) maxReportBytes, 1.0)));
        summary.put("history", history(historyFiles));
        summary.put("uploadLedger", uploadLedger);
        return summary;
    }

    private Map<String, Long> sortedFailures() {
        List<Map.Entry<String, AtomicLong>> entries = new ArrayList<>(failuresByType.entrySet());
        Collections.sort(entries, (a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
        Map<String, Long> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue().get());
        }
        return sorted;
    }

    private static Map<String, Object> history(List<Map.Entry<String, byte[]>> files) {
        long total = 0L;
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : files) {
            total += file.getValue().length;
            names.add(file.getKey());
        }
        Map<String, Object> history = new LinkedHashMap<>();
        history.put("fileCount", files.size());
        history.put("totalBytes", total);
        history.put("files", names);
        return history;
    }
}
