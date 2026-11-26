package org.pnfolder.pnlibrary.core.observability;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationKeys;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationRequest;
import org.pnfolder.pnlibrary.core.observability.store.ObservationJournal;
import org.pnfolder.pnlibrary.core.observability.store.ObservationRetention;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationJournalThrottleTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static Observation observation(int index, long at, ObservationLevel level, Long keepFor) {
        Map<String, String> data = keepFor == null ? Collections.emptyMap()
                : Collections.singletonMap(ObservationKeys.KEEP_UNTIL_MS, Long.toString(at + keepFor));
        return Observation.from(new ObservationRequest(null, null, "event " + index, level, data), at);
    }

    private static Observation observation(int index, long at) {
        return observation(index, at, ObservationLevel.INFO, null);
    }

    @Test
    void recentEqualsWhatRetentionOnEveryAppendWouldHaveKeptAlsoAfterAReload() throws IOException {
        Path root = Files.createTempDirectory("observation-equivalence");
        AtomicLong now = new AtomicLong();
        List<Observation> appended = new ArrayList<>();
        ObservationLevel[] levels = ObservationLevel.values();
        try (ObservationJournal journal = new ObservationJournal(root.resolve("observations.jsonl"), now::get)) {
            for (int index = 0; index < 400; index++) {
                now.set(index * (DAY / 20));
                Long keep = index % 11 == 0 ? Long.valueOf(3 * DAY) : null;
                Observation observation = observation(index, now.get(), levels[index % levels.length], keep);
                appended.add(observation);
                journal.append(observation);
                assertEquals(ObservationRetention.retained(appended, now.get()), journal.recent(), "after append " + index);
            }
        }
        try (ObservationJournal reloaded = new ObservationJournal(root.resolve("observations.jsonl"), now::get)) {
            assertEquals(ObservationRetention.retained(appended, now.get()), reloaded.recent());
        }
    }

    @Test
    void tenThousandAppendsRunRetentionAtMostTenTimes() throws IOException {
        Path root = Files.createTempDirectory("observation-throttle");
        AtomicInteger calls = new AtomicInteger();
        try (ObservationJournal journal = new ObservationJournal(root.resolve("observations.jsonl"), () -> 1_000,
                (all, now) -> {
                    calls.incrementAndGet();
                    return ObservationRetention.retained(all, now);
                })) {
            for (int index = 0; index < 10_000; index++) {
                journal.append(observation(index, 1_000));
            }
        }

        assertTrue(calls.get() <= 10, "retention calls: " + calls.get());
        try (ObservationJournal reloaded = new ObservationJournal(root.resolve("observations.jsonl"), () -> 1_000)) {
            assertEquals(10_000, reloaded.recent().size());
        }
    }

    @Test
    void aFullJournalIsCutByMarginRatherThanRewrittenOnEveryAppend() throws IOException {
        Path root = Files.createTempDirectory("observation-bound");
        AtomicInteger calls = new AtomicInteger();
        try (ObservationJournal journal = new ObservationJournal(root.resolve("observations.jsonl"), () -> 1_000,
                (all, now) -> {
                    calls.incrementAndGet();
                    return ObservationRetention.retained(all, now);
                })) {
            for (int index = 0; index < ObservationRetention.MAX_OBSERVATIONS + 1_200; index++) {
                journal.append(observation(index, 1_000));
            }
            assertEquals(ObservationRetention.MAX_OBSERVATIONS, journal.recent().size());
            assertEquals("event 1200", journal.recent().get(0).getMessage());
        }
        assertTrue(calls.get() <= 5, "retention calls: " + calls.get());
    }
}
