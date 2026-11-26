package org.pnfolder.pnlibrary.bukkit.tasks;

import java.time.Duration;

/** Converts durations to server ticks. */
final class TaskTicks {
    private static final double NANOS_PER_TICK = 50_000_000.0;

    private TaskTicks() {
    }

    /** Rounds a non-zero duration up to the next server tick. */
    static long durationToTicks(Duration value) {
        if (value.isZero()) {
            return 0;
        }
        return Math.max((long) Math.ceil(value.toNanos() / NANOS_PER_TICK), 1);
    }
}
