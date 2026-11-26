package org.pnfolder.pnlibrary.common.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Lets a failure through the first time and then at most once per interval for the same key, so a loop that keeps
 * failing fills the log once instead of on every pass.
 */
public final class FailureThrottle {
    private static final long DEFAULT_INTERVAL_MILLIS = 60_000;
    // Keys come from failure sites in code, so the set is small; the cap only guards against unbounded keys.
    private static final int MAX_KEYS = 256;

    private final long intervalMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Long> lastReported = new ConcurrentHashMap<>();

    /** A throttle with a one-minute interval that reads the system clock. */
    public FailureThrottle() {
        this(DEFAULT_INTERVAL_MILLIS);
    }

    /** A throttle that reads the system clock. */
    public FailureThrottle(long intervalMillis) {
        this(intervalMillis, System::currentTimeMillis);
    }

    /** A throttle that reads time from {@code clock}, which tests use to move time forward. */
    public FailureThrottle(long intervalMillis, @NotNull LongSupplier clock) {
        this.intervalMillis = intervalMillis;
        this.clock = clock;
    }

    /**
     * Returns whether the failure with this key should be written now; later calls inside the interval return false.
     */
    public boolean shouldReport(@NotNull String key) {
        long now = clock.getAsLong();
        if (lastReported.size() >= MAX_KEYS) {
            lastReported.entrySet().removeIf(entry -> now - entry.getValue() >= intervalMillis);
        }
        boolean[] allowed = {false};
        lastReported.compute(key, (name, last) -> {
            allowed[0] = last == null || now - last >= intervalMillis;
            return allowed[0] ? now : last;
        });
        return allowed[0];
    }
}
