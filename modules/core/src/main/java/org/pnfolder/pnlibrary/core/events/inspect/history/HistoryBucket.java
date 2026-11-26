package org.pnfolder.pnlibrary.core.events.inspect.history;

import java.util.concurrent.ConcurrentHashMap;

/** The hour and day that calls belong to from {@code validFrom} until {@code validUntil}; replaced when the minute changes. */
final class HistoryBucket {
    private final long validFrom;
    private final long validUntil;
    private final ConcurrentHashMap<String, EventCounts> hourCounts;
    private final ConcurrentHashMap<String, EventCounts> dayCounts;

    HistoryBucket(
            long validFrom,
            long validUntil,
            ConcurrentHashMap<String, EventCounts> hourCounts,
            ConcurrentHashMap<String, EventCounts> dayCounts) {
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.hourCounts = hourCounts;
        this.dayCounts = dayCounts;
    }

    boolean covers(long millis) {
        return millis >= validFrom && millis < validUntil;
    }

    ConcurrentHashMap<String, EventCounts> getHourCounts() {
        return hourCounts;
    }

    ConcurrentHashMap<String, EventCounts> getDayCounts() {
        return dayCounts;
    }
}
