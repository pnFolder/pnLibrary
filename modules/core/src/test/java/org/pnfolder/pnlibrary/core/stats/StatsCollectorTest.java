package org.pnfolder.pnlibrary.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.pnfolder.pnlibrary.common.lang.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;

class StatsCollectorTest {
    @TempDir Path directory;

    private final long now = System.currentTimeMillis();

    private JournalStore journal() {
        Map<JournalLevel, Duration> retention = new EnumMap<>(JournalLevel.class);
        for (JournalLevel level : JournalLevel.values()) {
            retention.put(level, Duration.ofDays(30));
        }
        return new JournalStore(directory, retention, Clock.systemUTC(), 8L * 1024 * 1024, 256L * 1024 * 1024, 64L * 1024 * 1024);
    }

    private JournalRecord record(JournalLevel level, String source, long ageHours) {
        return new JournalRecord(now - Duration.ofHours(ageHours).toMillis(), level, source, "LOG", source + " " + level,
            new HashMap<>());
    }

    private StatsCollector collector(JournalStore journal, List<EventInfo> events) {
        return new StatsCollector(() -> Collections.emptyList(), () -> events, journal, () -> now);
    }

    @Test
    void countsTheErrorsAndWarningsOfTheLastDayAndRanksThePlugins() {
        JournalStore journal = journal();
        for (int i = 0; i < 3; i++) {
            journal.append(record(JournalLevel.ERROR, "pnmarket", 0));
        }
        journal.append(record(JournalLevel.ERROR, "pnclans", 0));
        journal.append(record(JournalLevel.ERROR, "pnclans", 48));
        journal.append(record(JournalLevel.WARNING, "pnmarket", 0));

        StatsSnapshot snapshot = collector(journal, Collections.emptyList()).collect();

        assertEquals(4, snapshot.getErrors24h());
        assertEquals(1, snapshot.getWarnings24h());
        assertEquals(Arrays.asList(new Pair<>("pnmarket", 3), new Pair<>("pnclans", 1)),
            snapshot.getErrorSources());
    }

    @Test
    void addsUpTheCallsAndDenialsOfEveryEvent() {
        List<EventInfo> events = Arrays.asList(new EventInfo("a.One", 2, 10, 1), new EventInfo("a.Two", 1, 5, 0));

        StatsSnapshot snapshot = collector(journal(), events).collect();

        assertEquals(15, snapshot.getEventsCalled());
        assertEquals(1, snapshot.getEventsDenied());
    }

    @Test
    void aStoredSnapshotIsReadBackAndDoesNotShowUpAsAJournalEvent() {
        JournalStore journal = journal();
        journal.append(record(JournalLevel.ERROR, "pnmarket", 0));
        StatsRecorder recorder = new StatsRecorder(collector(journal, Collections.emptyList()), journal);

        recorder.record();

        List<StatsSnapshot> history = recorder.history(1, 5);
        assertEquals(1, history.size());
        assertEquals(1, history.get(0).getErrors24h());
        for (JournalRecord stored : journal.read(Collections.singleton(JournalLevel.INFO), null, null, 100, null)) {
            assertTrue(!StatsSnapshot.RECORD_TYPE.equals(stored.getType()));
        }
    }

    @Test
    void aSnapshotThatCannotBeTakenIsReportedAndDoesNotReachTheCaller() {
        JournalStore journal = journal();
        List<Throwable> failures = new ArrayList<>();
        StatsCollector broken = new StatsCollector(() -> {
            throw new IllegalStateException("tasks unavailable");
        }, () -> Collections.emptyList(), journal);

        new StatsRecorder(broken, journal, failures::add).record();

        assertEquals(1, failures.size());
        assertEquals("tasks unavailable", failures.get(0).getMessage());
    }
}
