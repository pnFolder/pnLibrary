package org.pnfolder.pnlibrary.core.observability.store;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationKeys;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** How long observations live: by level, or by the author's own period. */
public final class ObservationRetention {
    /** Hard bound prevents even critical observations from growing the journal forever. */
    public static final int MAX_OBSERVATIONS = 10_000;

    private ObservationRetention() {
    }

    /** The observations still worth keeping at {@code now}: how long each level lives, critical ones until the hard size bound. */
    @NotNull
    public static List<Observation> retained(@NotNull Collection<Observation> observations, long now) {
        List<Observation> active = new ArrayList<>();
        for (Observation observation : observations) {
            if (isActive(observation, now)) {
                active.add(observation);
            }
        }
        int from = Math.max(0, active.size() - MAX_OBSERVATIONS);
        return new ArrayList<>(active.subList(from, active.size()));
    }

    /** The number in {@code text}, or {@code null} when it is missing or not a number. */
    @Nullable
    public static Long parseMillis(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** The author's period, when set, replaces the level's default in both directions. */
    private static boolean isActive(Observation observation, long now) {
        Long keepUntil = parseMillis(observation.getData().get(ObservationKeys.KEEP_UNTIL_MS));
        if (keepUntil != null) {
            return now <= keepUntil;
        }
        ObservationLevel level = observation.getLevel();
        return level == ObservationLevel.CRITICAL || now - observation.getTimestamp() <= lifetime(level);
    }

    private static long lifetime(ObservationLevel level) {
        switch (level) {
            case TRACE:
                return Duration.ofHours(6).toMillis();
            case INFO:
                return Duration.ofDays(7).toMillis();
            case NOTICE:
            case WARNING:
                return Duration.ofDays(30).toMillis();
            case ERROR:
                return Duration.ofDays(90).toMillis();
            default:
                return Long.MAX_VALUE;
        }
    }
}
