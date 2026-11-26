package org.pnfolder.pnlibrary.core.tasks;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** The counters and times a task keeps about its runs; read for its snapshot. */
final class TaskRunStats {
    private final AtomicLong runs = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong failedRuns = new AtomicLong();
    private final AtomicLong failureStreak = new AtomicLong();
    private volatile Instant started;
    private volatile Instant completed;
    private volatile String failure;
    private volatile Duration lastDuration;
    private volatile Duration lastLag;
    private volatile Duration worstLag;

    long getRuns() {
        return runs.get();
    }

    long startRun() {
        return runs.incrementAndGet();
    }

    long getSkipped() {
        return skipped.get();
    }

    void skip() {
        skipped.incrementAndGet();
    }

    long getFailedRuns() {
        return failedRuns.get();
    }

    void failRun() {
        failedRuns.incrementAndGet();
    }

    long failInARow() {
        return failureStreak.incrementAndGet();
    }

    void succeeded(Duration duration) {
        failureStreak.set(0);
        lastDuration = duration;
    }

    void lag(Duration lag) {
        lastLag = lag;
        Duration worst = worstLag;
        if (worst == null || lag.compareTo(worst) > 0) {
            worstLag = lag;
        }
    }

    @Nullable
    Instant getStarted() {
        return started;
    }

    void setStarted(Instant value) {
        started = value;
    }

    @Nullable
    Instant getCompleted() {
        return completed;
    }

    void setCompleted(Instant value) {
        completed = value;
    }

    @Nullable
    String getFailure() {
        return failure;
    }

    void setFailure(String value) {
        failure = value;
    }

    @Nullable
    Duration getLastDuration() {
        return lastDuration;
    }

    @Nullable
    Duration getLastLag() {
        return lastLag;
    }

    @Nullable
    Duration getWorstLag() {
        return worstLag;
    }
}
