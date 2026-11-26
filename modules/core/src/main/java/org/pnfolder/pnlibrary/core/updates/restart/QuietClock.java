package org.pnfolder.pnlibrary.core.updates.restart;

import java.time.Duration;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/** Measures how long the number of players has stayed at or below a limit without a break. */
public final class QuietClock {
    private Instant since;

    /** Notes the players seen at {@code now}; returns how long there have been {@code max} or fewer, zero otherwise. */
    public synchronized @NotNull Duration observe(int players, int max, @NotNull Instant now) {
        if (players > max) {
            since = null;
            return Duration.ZERO;
        }
        if (since == null) {
            since = now;
        }
        return Duration.between(since, now);
    }

    /** Forgets what was seen, so the next observation starts the count again. */
    public synchronized void reset() {
        since = null;
    }
}
