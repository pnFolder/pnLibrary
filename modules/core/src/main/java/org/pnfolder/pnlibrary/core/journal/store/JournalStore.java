package org.pnfolder.pnlibrary.core.journal.store;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Segmented, block-compressed journal on disk.
 *
 * <p>Segments are named {@code <level>-<yyyy-MM-dd>[-n].pnj}; the active segment is tracked in memory so the folder
 * is listed only on first use per day. ERROR blocks are forced to disk before {@code append} returns, so a crash right
 * after cannot lose an error record. Retention is per level, so errors are kept longer than routine info lines.
 */
public final class JournalStore {
    // More than SUMMARY_LIMIT records in 24 h is reported as "at least".
    private static final int SUMMARY_LIMIT = 10_000;
    // Far below the decoder's limit of 10 000.
    private static final int BATCH_RECORDS = 256;

    private static final class Segment {
        final Path path;
        long bytes;

        Segment(Path path, long bytes) {
            this.path = path;
            this.bytes = bytes;
        }
    }

    private final Path directory;
    private final Clock clock;
    private final long maximumSegmentBytes;
    private final long maximumDiskBytes;
    private final long maximumReadBytes;
    private final JournalFiles files;
    private final JournalReader reader;
    private final JournalRetention retentionRules;
    private final Map<JournalDay, Segment> active = new HashMap<JournalDay, Segment>();
    private final Set<LocalDate> loadedDays = new HashSet<LocalDate>();
    // One per chunk, not per record.
    private final AtomicLong blocksWritten = new AtomicLong();
    private LocalDate lastPrunedOn;

    public JournalStore(@NotNull Path directory, @NotNull Map<JournalLevel, Duration> retention) {
        this(directory, retention, Clock.systemUTC());
    }

    public JournalStore(@NotNull Path directory, @NotNull Map<JournalLevel, Duration> retention,
                        @NotNull Clock clock) {
        this(directory, retention, clock, 8L * 1024 * 1024, 256L * 1024 * 1024, 64L * 1024 * 1024);
    }

    public JournalStore(@NotNull Path directory, @NotNull Map<JournalLevel, Duration> retention,
                        @NotNull Clock clock, long maximumSegmentBytes, long maximumDiskBytes,
                        long maximumReadBytes) {
        for (JournalLevel level : JournalLevel.values()) {
            if (!retention.containsKey(level)) {
                throw new IllegalArgumentException("Every journal level needs a retention period");
            }
        }
        if (maximumSegmentBytes <= 0 || maximumDiskBytes < maximumSegmentBytes || maximumReadBytes <= 0) {
            throw new IllegalArgumentException("Journal byte limits are inconsistent");
        }
        this.directory = directory;
        this.clock = clock;
        this.maximumSegmentBytes = maximumSegmentBytes;
        this.maximumDiskBytes = maximumDiskBytes;
        this.maximumReadBytes = maximumReadBytes;
        this.files = new JournalFiles(directory);
        this.reader = new JournalReader(files, maximumReadBytes);
        this.retentionRules = new JournalRetention(directory, files, retention, clock, maximumDiskBytes,
            this::forgetSegments);
    }

    /** How often the folder was listed; tests use it to prove that appends do not scan the folder. */
    public int getDirectoryListings() {
        return files.getListings().get();
    }

    /** Blocks written so far: one per chunk, not per record. */
    public @NotNull AtomicLong getBlocksWritten() {
        return blocksWritten;
    }

    /** Stores one record; a record that cannot be stored throws {@link IllegalArgumentException}. */
    public void append(@NotNull JournalRecord record) {
        appendAll(Collections.singletonList(record), (rejected, error) -> {
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            }
            throw new IllegalStateException(error);
        });
    }

    /** Stores {@code records} as one block per level, day and {@value #BATCH_RECORDS} records, so a burst costs one write. */
    public synchronized void appendAll(@NotNull List<JournalRecord> records, @NotNull JournalRejection rejected) {
        if (records.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(directory);
            pruneOncePerDay();
            for (Map.Entry<JournalDay, List<JournalRecord>> group : groupByDay(records).entrySet()) {
                List<JournalRecord> all = group.getValue();
                for (int from = 0; from < all.size(); from += BATCH_RECORDS) {
                    List<JournalRecord> chunk = all.subList(from, Math.min(all.size(), from + BATCH_RECORDS));
                    writeChunk(group.getKey(), chunk, rejected);
                }
            }
            if (retentionRules.enforcementDue()) {
                retentionRules.enforce(files.all());
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static Map<JournalDay, List<JournalRecord>> groupByDay(List<JournalRecord> records) {
        Map<JournalDay, List<JournalRecord>> groups = new LinkedHashMap<JournalDay, List<JournalRecord>>();
        for (JournalRecord record : records) {
            JournalDay day = new JournalDay(record.getLevel(), utcDate(record.getTimeMillis()));
            List<JournalRecord> group = groups.get(day);
            if (group == null) {
                group = new ArrayList<JournalRecord>();
                groups.put(day, group);
            }
            group.add(record);
        }
        return groups;
    }

    private void pruneOncePerDay() {
        LocalDate today = retentionRules.today();
        if (today.equals(lastPrunedOn)) {
            return;
        }
        lastPrunedOn = today;
        forgetSegments();
        Failures.runCatchingNonFatal(this::prune);
    }

    private void writeChunk(JournalDay day, List<JournalRecord> chunk, JournalRejection rejected)
        throws IOException {
        byte[] block;
        try {
            block = JournalBlockCodec.encode(chunk);
            if (block.length > maximumDiskBytes) {
                throw new IllegalArgumentException("Journal block exceeds disk budget");
            }
        } catch (IllegalArgumentException error) {
            if (chunk.size() == 1) {
                rejected.rejected(chunk.get(0), error);
                return;
            }
            for (JournalRecord single : new ArrayList<JournalRecord>(chunk)) {
                writeChunk(day, Collections.singletonList(single), rejected);
            }
            return;
        }
        Segment segment = segmentFor(day, block.length);
        try (FileChannel channel = FileChannel.open(segment.path, StandardOpenOption.CREATE,
            StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer bytes = ByteBuffer.wrap(block);
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            if (day.level() == JournalLevel.ERROR) {
                channel.force(true);
            }
        }
        segment.bytes += block.length;
        blocksWritten.incrementAndGet();
    }

    private Segment segmentFor(JournalDay day, int size) throws IOException {
        if (loadedDays.add(day.date())) {
            loadDay(day.date());
        }
        Segment current = active.get(day);
        if (current != null && current.bytes + size <= maximumSegmentBytes) {
            return current;
        }
        retentionRules.segmentStarted();
        int number = current == null ? 0 : files.segmentOf(current.path) + 1;
        Segment next = new Segment(files.pathOf(day.level(), day.date(), number), 0);
        active.put(day, next);
        return next;
    }

    private void loadDay(LocalDate date) throws IOException {
        List<Path> all = files.all();
        for (JournalLevel level : JournalLevel.values()) {
            Path newest = null;
            for (Path file : files.of(level, all)) {
                if (date.equals(files.dateOf(file)) && (newest == null
                    || files.segmentOf(newest) < files.segmentOf(file))) {
                    newest = file;
                }
            }
            if (newest != null) {
                active.put(new JournalDay(level, date), new Segment(newest, Files.size(newest)));
            }
        }
    }

    /** The newest records, at most {@code limit}; empty {@code levels} means every level. */
    public @NotNull List<JournalRecord> read(@NotNull Set<JournalLevel> levels, @Nullable String source,
                                             @Nullable Long sinceMillis, int limit, @Nullable String type) {
        return reader.readSnapshot(levels, source, sinceMillis, limit, type).getRecords();
    }

    public @NotNull List<JournalRecord> read() {
        return read(Collections.<JournalLevel>emptySet(), null, null, 100, null);
    }

    public @NotNull List<JournalRecord> read(int limit) {
        return read(Collections.<JournalLevel>emptySet(), null, null, limit, null);
    }

    /** The last 24 hours per level as counts and limits, without any message text. */
    public @NotNull Map<String, Object> summary() {
        long since = clock.millis() - Duration.ofHours(24).toMillis();
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        for (JournalLevel level : JournalLevel.values()) {
            JournalSnapshot snapshot = reader.readSnapshot(Collections.singleton(level), null, since,
                SUMMARY_LIMIT, null);
            List<JournalRecord> recent = snapshot.getRecords();
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("recordsLast24h", recent.size());
            entry.put("countCapped", recent.size() >= SUMMARY_LIMIT || snapshot.isTruncated());
            entry.put("readBudgetExhausted", snapshot.isTruncated());
            entry.put("maximumReadBytes", maximumReadBytes);
            entry.put("maximumDiskBytes", maximumDiskBytes);
            entry.put("newestAtMillis", recent.isEmpty() ? null : recent.get(0).getTimeMillis());
            entry.put("retentionDays", retentionRules.retentionDays(level));
            summary.put(level.getFileGroup(), entry);
        }
        return summary;
    }

    /** Deletes files older than their level's retention and enforces the disk budget; returns how many were removed. */
    public synchronized int prune() {
        try {
            return retentionRules.prune();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private void forgetSegments() {
        active.clear();
        loadedDays.clear();
    }

    private static LocalDate utcDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
