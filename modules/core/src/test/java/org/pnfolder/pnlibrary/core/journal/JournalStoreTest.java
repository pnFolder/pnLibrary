package org.pnfolder.pnlibrary.core.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;

class JournalStoreTest {
    @TempDir Path directory;

    private final Instant day = Instant.parse("2026-10-19T10:00:00Z");
    private final Map<JournalLevel, Duration> retention = new EnumMap<>(JournalLevel.class);
    private static final Set<JournalLevel> NONE = Collections.emptySet();

    JournalStoreTest() {
        retention.put(JournalLevel.INFO, Duration.ofDays(7));
        retention.put(JournalLevel.WARNING, Duration.ofDays(30));
        retention.put(JournalLevel.ERROR, Duration.ofDays(90));
    }

    private JournalStore store(Instant at) {
        return new JournalStore(directory, retention, Clock.fixed(at, ZoneOffset.UTC));
    }

    private JournalStore store(long segmentBytes, long diskBytes, long readBytes) {
        return new JournalStore(directory, retention, Clock.fixed(day, ZoneOffset.UTC), segmentBytes, diskBytes,
            readBytes);
    }

    private JournalRecord record(JournalLevel level, String source, String message, Instant at) {
        return new JournalRecord(at.toEpochMilli(), level, source, "LOG", message,
            Collections.singletonMap("player", "Steve"));
    }

    private static List<String> messages(List<JournalRecord> records) {
        List<String> result = new ArrayList<>();
        for (JournalRecord record : records) {
            result.add(record.getMessage());
        }
        return result;
    }

    private List<JournalRecord> read(JournalStore journal, Set<JournalLevel> levels, String source, int limit) {
        return journal.read(levels, source, null, limit, null);
    }

    private Path onlyFile(String group) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(path -> path.getFileName().toString().startsWith(group + "-"))
                .collect(Collectors.toList()).get(0);
        }
    }

    private void deleteAll() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.collect(Collectors.toList())) {
                Files.delete(file);
            }
        }
    }

    private long totalBytes() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            long sum = 0;
            for (Path file : files.collect(Collectors.toList())) {
                sum += Files.size(file);
            }
            return sum;
        }
    }

    @Test
    void recordsRoundTripNewestFirstAndFilterByLevelAndSource() {
        JournalStore journal = store(day);
        journal.append(record(JournalLevel.INFO, "shop", "opened", day));
        journal.append(record(JournalLevel.ERROR, "shop", "payout failed", day.plusSeconds(60)));
        journal.append(record(JournalLevel.WARNING, "pnlibrary", "slow disk", day.plusSeconds(30)));

        List<JournalRecord> all = journal.read();
        assertEquals(Arrays.asList("payout failed", "slow disk", "opened"), messages(all));
        assertEquals(Collections.singletonMap("player", "Steve"), all.get(0).getDetails());
        assertEquals(Arrays.asList("payout failed"),
            messages(read(journal, Collections.singleton(JournalLevel.ERROR), null, 100)));
        assertEquals(Arrays.asList("payout failed", "opened"), messages(read(journal, NONE, "shop", 100)));
    }

    @Test
    void aDamagedBlockIsSkippedAndTheRestOfTheFileSurvives() throws IOException {
        JournalStore journal = store(day);
        journal.append(record(JournalLevel.ERROR, "shop", "first", day));
        journal.append(record(JournalLevel.ERROR, "shop", "second", day));
        Path file = onlyFile("error");
        byte[] bytes = Files.readAllBytes(file);
        bytes[12] = (byte) (bytes[12] ^ 0x7F);
        Files.write(file, bytes);

        assertEquals(Arrays.asList("second"),
            messages(read(journal, Collections.singleton(JournalLevel.ERROR), null, 100)));
    }

    @Test
    void anInterruptedWriteAtTheEndOfTheFileLeavesEarlierRecordsReadable() throws IOException {
        JournalStore journal = store(day);
        journal.append(record(JournalLevel.ERROR, "shop", "complete", day));
        Files.write(onlyFile("error"), new byte[] {0, 0, 1, 0, 9, 9}, StandardOpenOption.APPEND);

        assertEquals(Arrays.asList("complete"),
            messages(read(journal, Collections.singleton(JournalLevel.ERROR), null, 100)));
    }

    @Test
    void retentionRemovesOldInfoButKeepsErrorsThatAreStillWithinTheirLongerLimit() throws IOException {
        JournalStore first = store(day);
        first.append(record(JournalLevel.INFO, "shop", "routine", day));
        first.append(record(JournalLevel.ERROR, "shop", "important", day));

        JournalStore eightDaysLater = store(day.plus(Duration.ofDays(8)));
        assertEquals(1, eightDaysLater.prune());

        assertEquals(Collections.emptyList(),
            read(eightDaysLater, Collections.singleton(JournalLevel.INFO), null, 100));
        assertEquals(Arrays.asList("important"),
            messages(read(eightDaysLater, Collections.singleton(JournalLevel.ERROR), null, 100)));
        assertTrue(Files.exists(onlyFile("error")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void summaryCountsTheLastDayPerLevelAndCarriesNoMessageText() {
        JournalStore journal = store(day);
        journal.append(record(JournalLevel.ERROR, "shop", "secret player data", day.minusSeconds(60)));
        journal.append(record(JournalLevel.ERROR, "shop", "another", day.minusSeconds(30)));
        journal.append(record(JournalLevel.ERROR, "shop", "too old", day.minus(Duration.ofHours(30))));

        Map<String, Object> errors = (Map<String, Object>) journal.summary().get("error");

        assertEquals(2, errors.get("recordsLast24h"));
        assertEquals(90L, errors.get("retentionDays"));
        assertEquals(day.minusSeconds(30).toEpochMilli(), errors.get("newestAtMillis"));
        assertFalse(journal.summary().toString().contains("secret"));
    }

    @Test
    void aWholeStackTraceWithItsCausesSurvivesStorage() {
        JournalStore journal = store(day);
        String trace = JournalRecord.stackTraceOf(new IllegalStateException("outer", new RuntimeException("inner cause")));
        Map<String, String> details = new LinkedHashMap<>();
        details.put("exception", "IllegalStateException");
        details.put(JournalRecord.STACKTRACE, trace);
        journal.append(new JournalRecord(day.toEpochMilli(), JournalLevel.ERROR, "shop", "ERROR", "failed", details));

        String stored = journal.read().get(0).getDetails().get(JournalRecord.STACKTRACE);

        assertEquals(trace, stored);
        assertTrue(stored.contains("Caused by: java.lang.RuntimeException: inner cause"));
    }

    @Test
    void segmentsRotateAndDiskRetentionKeepsTheNewestEvents() throws IOException {
        JournalStore journal = store(180, 700, 64L * 1024 * 1024);
        for (int n = 0; n < 40; n++) {
            journal.append(record(JournalLevel.INFO, "shop", "event-" + n, day.plusSeconds(n)));
        }
        assertTrue(totalBytes() <= 700);
        assertEquals(Arrays.asList("event-39", "event-38", "event-37"), messages(journal.read(3)));
    }

    @Test
    void streamingSelectionPreservesNewestTimestampsEvenWhenRecordsArriveOutOfOrder() {
        JournalStore journal = store(day);
        for (long n : new long[] {3, 1, 5, 2, 4}) {
            journal.append(record(JournalLevel.INFO, "shop", String.valueOf(n), day.plusSeconds(n)));
        }
        assertEquals(Arrays.asList("5", "4"), messages(journal.read(2)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readBudgetExhaustionIsVisibleInsteadOfReadingAnEntireOversizedHistory() {
        JournalStore journal = store(8L * 1024 * 1024, 256L * 1024 * 1024, 8);
        journal.append(record(JournalLevel.ERROR, "shop", "error", day));
        assertTrue(journal.read().isEmpty());
        Map<String, Object> errors = (Map<String, Object>) journal.summary().get("error");
        assertEquals(true, errors.get("readBudgetExhausted"));
        assertEquals(true, errors.get("countCapped"));
    }

    @Test
    void diskBudgetEvictsRoutineFilesBeforeErrorFiles() throws IOException {
        // The error is older than every routine record, so plain oldest-first eviction would delete it first.
        Instant olderDay = day.minus(Duration.ofDays(1));
        store(day).append(record(JournalLevel.ERROR, "shop", "kept", olderDay));
        long errorBytes = Files.size(onlyFile("error"));
        store(day).append(record(JournalLevel.INFO, "shop", "routine", day));
        long infoBytes = Files.size(onlyFile("info"));
        deleteAll();

        // One error block plus one routine block exceeds this budget, so each new routine block forces eviction.
        JournalStore journal = store(1, Math.max(errorBytes, infoBytes) + 1, 64L * 1024 * 1024);
        journal.append(record(JournalLevel.ERROR, "shop", "kept", olderDay));
        for (int i = 0; i < 3; i++) {
            journal.append(record(JournalLevel.INFO, "shop", "routine", day));
        }

        assertEquals(Arrays.asList("kept"),
            messages(read(store(day), Collections.singleton(JournalLevel.ERROR), null, 100)));
        assertEquals(Collections.emptyList(), read(store(day), Collections.singleton(JournalLevel.INFO), null, 100));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readBudgetNeverReturnsTheStalePrefixOfAFileItCannotReadWhole() throws IOException {
        store(day).append(record(JournalLevel.INFO, "shop", "first", day));
        long oneRecordBytes = Files.size(onlyFile("info"));
        deleteAll();

        // Both records share one file that is larger than the budget; a prefix read would return only "first".
        JournalStore journal = store(8L * 1024 * 1024, 256L * 1024 * 1024, oneRecordBytes + 1);
        journal.append(record(JournalLevel.INFO, "shop", "first", day));
        journal.append(record(JournalLevel.INFO, "shop", "second", day.plusSeconds(1)));

        assertEquals(Collections.emptyList(), read(journal, Collections.singleton(JournalLevel.INFO), null, 100));
        Map<String, Object> info = (Map<String, Object>) journal.summary().get("info");
        assertEquals(true, info.get("readBudgetExhausted"));
    }
}
