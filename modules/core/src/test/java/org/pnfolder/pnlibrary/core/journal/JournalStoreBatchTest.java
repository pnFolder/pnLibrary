package org.pnfolder.pnlibrary.core.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.core.journal.store.JournalQueries;
import org.pnfolder.pnlibrary.core.journal.store.JournalRejection;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;

class JournalStoreBatchTest {
    @TempDir Path directory;

    private final Instant day = Instant.parse("2026-10-19T10:00:00Z");
    private final Map<JournalLevel, Duration> retention = new EnumMap<>(JournalLevel.class);
    private final JournalRejection throwing = (record, error) -> {
        throw (RuntimeException) error;
    };

    JournalStoreBatchTest() {
        for (JournalLevel level : JournalLevel.values()) {
            retention.put(level, Duration.ofDays(30));
        }
    }

    private List<JournalRecord> corpus(int count) {
        List<JournalRecord> records = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Map<String, String> details = new LinkedHashMap<>();
            if (index % 7 == 0) {
                details.put("exception", "X");
                details.put(JournalRecord.STACKTRACE, "trace " + index);
            }
            records.add(new JournalRecord(day.toEpochMilli() + index, JournalLevel.values()[index % 3],
                "src" + (index % 4), "LOG", "message " + index, details));
        }
        return records;
    }

    private static JournalRecord withLevel(JournalRecord r, JournalLevel level) {
        return new JournalRecord(r.getTimeMillis(), level, r.getSource(), r.getType(), r.getMessage(), r.getDetails());
    }

    private static List<JournalRecord> allAs(List<JournalRecord> records, JournalLevel level) {
        List<JournalRecord> result = new ArrayList<>();
        for (JournalRecord record : records) {
            result.add(withLevel(record, level));
        }
        return result;
    }

    private JournalStore store(Path folder) {
        return new JournalStore(folder, retention, Clock.fixed(day, ZoneOffset.UTC));
    }

    private List<JournalRecord> readAll(JournalStore store, int limit) {
        return store.read(Collections.emptySet(), null, null, limit, null);
    }

    private long fileCount(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.count();
        }
    }

    @Test
    void aBatchReadsBackExactlyLikeTheSameRecordsAppendedOneByOne() throws IOException {
        Path one = Files.createDirectory(directory.resolve("one"));
        Path batch = Files.createDirectory(directory.resolve("batch"));
        List<JournalRecord> records = corpus(900);
        JournalStore single = store(one);
        for (JournalRecord record : records) {
            single.append(record);
        }
        store(batch).appendAll(records, throwing);

        List<JournalRecord> expected = readAll(store(one), 100_000);
        assertEquals(900, expected.size());
        assertEquals(expected, readAll(store(batch), 100_000));
        Set<JournalLevel> errors = Collections.singleton(JournalLevel.ERROR);
        assertEquals(store(one).read(errors, null, null, 50, null), store(batch).read(errors, null, null, 50, null));
    }

    @Test
    void blocksOfTheOldOneRecordFormatAndNewBatchesLiveInOneFile() throws IOException {
        store(directory).append(new JournalRecord(day.toEpochMilli(), JournalLevel.INFO, "a", "LOG", "legacy"));
        JournalStore reopened = store(directory);
        List<JournalRecord> info = new ArrayList<>();
        for (JournalRecord record : corpus(30)) {
            if (record.getLevel() == JournalLevel.INFO) {
                info.add(record);
            }
        }
        reopened.appendAll(info, throwing);

        assertEquals(1, fileCount(directory));
        boolean found = false;
        for (JournalRecord record : readAll(reopened, 100)) {
            found |= record.getMessage().equals("legacy");
        }
        assertTrue(found);
        assertEquals(11, readAll(reopened, 100).size());
    }

    @Test
    void aThousandRecordsCostAtMostThreeListingsAndOneBlockPerChunk() {
        JournalStore journal = store(directory);
        List<JournalRecord> records = allAs(corpus(1_000), JournalLevel.INFO);
        for (int from = 0; from < records.size(); from += 250) {
            journal.appendAll(records.subList(from, from + 250), throwing);
        }

        assertTrue(journal.getDirectoryListings() <= 3, "listings: " + journal.getDirectoryListings());
        assertTrue(journal.getBlocksWritten().get() <= 1_000 / 250, "blocks: " + journal.getBlocksWritten().get());
        assertEquals(1_000, readAll(journal, 100_000).size());
    }

    @Test
    void singleAppendsNoLongerListTheFolderPerRecord() {
        JournalStore journal = store(directory);
        for (JournalRecord record : allAs(corpus(1_000), JournalLevel.INFO)) {
            journal.append(record);
        }

        assertTrue(journal.getDirectoryListings() <= 3, "listings: " + journal.getDirectoryListings());
        assertEquals(1_000, readAll(journal, 100_000).size());
    }

    @Test
    void aFullSegmentRollsOverToTheNextNumberAndSurvivesARestart() throws IOException {
        Clock clock = Clock.fixed(day, ZoneOffset.UTC);
        long big = 256L * 1024 * 1024;
        JournalStore small = new JournalStore(directory, retention, clock, 2_000, big, 64L * 1024 * 1024);
        List<JournalRecord> records = allAs(corpus(600), JournalLevel.WARNING);
        for (int from = 0; from < records.size(); from += 20) {
            small.appendAll(records.subList(from, from + 20), throwing);
        }
        JournalStore restarted = new JournalStore(directory, retention, clock, 2_000, big, 64L * 1024 * 1024);
        restarted.appendAll(Collections.singletonList(new JournalRecord(records.get(0).getTimeMillis(),
            JournalLevel.WARNING, records.get(0).getSource(), "LOG", "after restart")), throwing);

        assertTrue(fileCount(directory) > 3, "files: " + fileCount(directory));
        assertEquals(601, readAll(restarted, 100_000).size());
    }

    @Test
    void theDiskBudgetIsCheckedAgainAfterAMinuteWithoutANewSegment() {
        AtomicLong now = new AtomicLong(day.toEpochMilli());
        Clock clock = new Clock() {
            @Override public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override public Instant instant() {
                return Instant.ofEpochMilli(now.get());
            }
        };
        JournalStore journal = new JournalStore(directory, retention, clock, 1_000_000, 1_000_000, 64L * 1024 * 1024);
        JournalRecord info = withLevel(corpus(1).get(0), JournalLevel.INFO);
        journal.append(info);
        int before = journal.getDirectoryListings();
        journal.append(info);
        assertEquals(before, journal.getDirectoryListings());

        now.addAndGet(61_000);
        journal.append(info);
        assertEquals(before + 1, journal.getDirectoryListings());
    }

    @Test
    void anOversizedRecordIsRejectedWithoutLosingItsNeighbours() {
        JournalStore journal = new JournalStore(directory, retention, Clock.fixed(day, ZoneOffset.UTC), 400, 400,
            64L * 1024 * 1024);
        Random noise = new Random(7);
        char[] chars = new char[4_000];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) ('a' + noise.nextInt(26));
        }
        JournalRecord bad = new JournalRecord(day.toEpochMilli(), JournalLevel.INFO, "a", "LOG", new String(chars));
        JournalRecord good = new JournalRecord(day.toEpochMilli() + 1, JournalLevel.INFO, "a", "LOG", "small");
        List<JournalRecord> rejected = new ArrayList<>();
        journal.appendAll(java.util.Arrays.asList(good, bad), (record, error) -> rejected.add(record));

        List<String> messages = new ArrayList<>();
        for (JournalRecord record : journal.read()) {
            messages.add(record.getMessage());
        }
        assertEquals(Collections.singletonList("small"), messages);
        assertEquals(Collections.singletonList(bad), rejected);
    }

    @Test
    void fingerprintKeepsItsOldValueAndIsComputedOnce() {
        Map<String, String> details = Collections.singletonMap("exception", "java.lang.Boom");
        JournalRecord record = new JournalRecord(1, JournalLevel.ERROR, "shop", "ERROR", "boom\nsecond line", details);
        String key = String.join("\u0000", "ERROR", "shop", "boom", "java.lang.Boom");
        assertEquals(String.format(Locale.ROOT, "%08x", key.hashCode()), record.getFingerprint());
        assertSame(record.getFingerprint(), record.getFingerprint());
        JournalRecord shortRecord = new JournalRecord(1, JournalLevel.INFO, "", "LOG", "");
        assertEquals(String.format(Locale.ROOT, "%08x", String.join("\u0000", "INFO", "", "", "").hashCode()),
            shortRecord.getFingerprint());
        for (int i = 0; i < 300; i++) {
            JournalRecord r = new JournalRecord(i, JournalLevel.WARNING, "s" + i, "LOG", "m" + i);
            String k = String.join("\u0000", "WARNING", "s" + i, "m" + i, "");
            assertEquals(String.format(Locale.ROOT, "%08x", k.hashCode()), r.getFingerprint());
        }
    }

    @Test
    void joinNoticeIsBuiltOnceForAWaveOfJoinsWithinTheCachePeriod() {
        JournalStore journal = store(directory);
        journal.append(new JournalRecord(day.toEpochMilli(), JournalLevel.ERROR, "shop", "ERROR", "payout failed"));
        AtomicLong now = new AtomicLong(day.toEpochMilli() + 60_000);
        JournalQueries queries = new JournalQueries(journal, new PnLibraryConfig(), day.toEpochMilli(), "/pn events",
            now::get);

        Object first = queries.joinNotice();
        int listings = journal.getDirectoryListings();
        for (int i = 0; i < 50; i++) {
            assertSame(first, queries.joinNotice());
        }
        assertEquals(listings, journal.getDirectoryListings());

        now.addAndGet(46_000);
        queries.joinNotice();
        assertTrue(journal.getDirectoryListings() > listings);
    }
}
