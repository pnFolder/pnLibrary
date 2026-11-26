package org.pnfolder.pnlibrary.core.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.core.journal.store.JournalQueries;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;

class JournalQueriesTest {
    private static final long MINUTE = 60_000L;

    @TempDir Path directory;

    private final long now = System.currentTimeMillis();

    private JournalQueries queries(JournalRecord... records) {
        Map<JournalLevel, Duration> retention = new EnumMap<>(JournalLevel.class);
        for (JournalLevel level : JournalLevel.values()) {
            retention.put(level, Duration.ofDays(30));
        }
        JournalStore store = new JournalStore(directory, retention, Clock.systemUTC());
        for (JournalRecord record : records) {
            store.append(record);
        }
        return new JournalQueries(store, new PnLibraryConfig(), now, "/pn events");
    }

    private JournalRecord record(JournalLevel level, String message, long agoMinutes) {
        return new JournalRecord(now - agoMinutes * MINUTE, level, "shop", "LOG", message);
    }

    private static List<String> messages(List<JournalGroup> groups) {
        List<String> result = new ArrayList<>();
        for (JournalGroup group : groups) {
            result.add(group.getMessage());
        }
        return result;
    }

    @Test
    void equalEventsMergeIntoOneGroupWithACount() {
        List<JournalGroup> groups = queries(
            record(JournalLevel.ERROR, "payout failed", 10),
            record(JournalLevel.ERROR, "payout failed", 5),
            record(JournalLevel.INFO, "opened", 1)).groups(null, 24, 10);

        assertEquals(Arrays.asList("opened", "payout failed"), messages(groups));
        assertEquals(2, groups.get(1).getCount());
        assertEquals(now - 5 * MINUTE, groups.get(1).getLastMillis());
    }

    @Test
    void levelAndWindowAndLimitNarrowTheGroups() {
        JournalQueries queries = queries(
            record(JournalLevel.ERROR, "old error", 60 * 30),
            record(JournalLevel.ERROR, "new error", 5),
            record(JournalLevel.INFO, "note", 4));

        assertEquals(Arrays.asList("new error"), messages(queries.groups("error", 24, 10)));
        assertEquals(Arrays.asList("note"), messages(queries.groups(null, 24, 1)));
        assertEquals(Arrays.asList("note", "new error", "old error"), messages(queries.groups(null, 48, 10)));
    }

    @Test
    void aGroupShowsOnlyTheFirstLineOfALongMessage() {
        List<JournalGroup> groups = queries(record(JournalLevel.WARNING, "first line\nsecond line", 1))
            .groups(null, 24, 10);

        assertEquals("first line", groups.get(0).getMessage());
    }

    @Test
    void detailSplitsTheStackTraceFromTheOtherFactsAndListsEveryTime() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("player", "Steve");
        details.put(JournalRecord.STACKTRACE, "boom\n at x");
        JournalQueries queries = queries(
            new JournalRecord(now - 10 * MINUTE, JournalLevel.ERROR, "shop", "LOG", "payout failed", details),
            new JournalRecord(now - 5 * MINUTE, JournalLevel.ERROR, "shop", "LOG", "payout failed", details));
        String id = queries.groups(null, 24, 10).get(0).getId();

        JournalDetail detail = queries.detail(id);
        assertEquals("boom\n at x", detail.getStackTrace());
        assertEquals(Collections.singletonMap("player", "Steve"), detail.getDetails());
        assertEquals(Arrays.asList(now - 5 * MINUTE, now - 10 * MINUTE), detail.getTimes());
    }

    @Test
    void detailOfAnUnknownEventIsNull() {
        assertNull(queries(record(JournalLevel.INFO, "opened", 1)).detail("ffffffff"));
    }
}
