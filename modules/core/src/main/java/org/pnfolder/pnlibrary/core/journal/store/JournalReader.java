package org.pnfolder.pnlibrary.core.journal.store;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/** Newest-first reads over the journal's segment files, bounded by a byte budget. */
public final class JournalReader {
    private final JournalFiles files;
    private final long maximumReadBytes;

    public JournalReader(@NotNull JournalFiles files, long maximumReadBytes) {
        this.files = Objects.requireNonNull(files, "files");
        this.maximumReadBytes = maximumReadBytes;
    }

    /** Newest-first read; {@link JournalSnapshot#isTruncated} tells whether the byte budget stopped it early. */
    public @NotNull JournalSnapshot readSnapshot(@NotNull Set<JournalLevel> levels, @Nullable String source,
                                                 @Nullable Long sinceMillis, int limit, @Nullable String type) {
        if (limit < 1 || limit > 100_000) {
            throw new IllegalArgumentException("Journal limit must be between 1 and 100000");
        }
        Set<JournalLevel> wanted = levels.isEmpty() ? EnumSet.allOf(JournalLevel.class) : levels;
        PriorityQueue<JournalRecord> newest = new PriorityQueue<>(
            Comparator.comparingLong(JournalRecord::getTimeMillis));
        RecordFilter filter = new RecordFilter(source, sinceMillis, type);
        long remaining = maximumReadBytes;
        LocalDate scannedDate = null;
        boolean truncated = false;
        for (Path file : segmentsNewestFirst(wanted, sinceMillis)) {
            LocalDate date = files.dateOf(file);
            if (newest.size() >= limit && !java.util.Objects.equals(date, scannedDate)) {
                break;
            }
            scannedDate = date;
            long size;
            try {
                size = Files.size(file);
                // Files are read whole and newest first: reading stops at a file that does not fit, so the
                // returned records are always the newest complete set rather than an arbitrary prefix.
                if (size > remaining) {
                    truncated = true;
                    break;
                }
                visitFile(file, size, newest, limit, filter);
            } catch (NoSuchFileException removed) {
                // Retention can remove a segment after the reader takes its snapshot.
                continue;
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
            remaining -= size;
        }
        List<JournalRecord> records = new ArrayList<>(newest);
        records.sort(Comparator.comparingLong(JournalRecord::getTimeMillis).reversed());
        return new JournalSnapshot(records, truncated);
    }

    private void visitFile(Path file, long size, PriorityQueue<JournalRecord> newest, int limit, RecordFilter filter)
        throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
            JournalBlockCodec.visit(input, size, record -> offer(newest, record, limit, filter));
        }
    }

    /** The segment files of {@code levels} that can hold records from {@code sinceMillis} on, newest day and segment first. */
    private List<Path> segmentsNewestFirst(Set<JournalLevel> levels, Long sinceMillis) {
        LocalDate sinceDate = sinceMillis == null ? null : utcDate(sinceMillis);
        List<Path> all = files.all();
        List<Path> segments = new ArrayList<>();
        for (JournalLevel level : levels) {
            for (Path file : files.of(level, all)) {
                LocalDate date = files.dateOf(file);
                if (sinceDate == null || date == null || !date.isBefore(sinceDate)) {
                    segments.add(file);
                }
            }
        }
        segments.sort((left, right) -> {
            int byDate = compareDates(files.dateOf(right), files.dateOf(left));
            return byDate != 0 ? byDate : Integer.compare(files.segmentOf(right), files.segmentOf(left));
        });
        return segments;
    }

    private static int compareDates(LocalDate left, LocalDate right) {
        if (left == null || right == null) {
            return left == right ? 0 : left == null ? -1 : 1;
        }
        return left.compareTo(right);
    }

    /** Keeps {@code record} in {@code newest} when it matches the filters and is among the newest seen so far. */
    private static void offer(PriorityQueue<JournalRecord> newest, JournalRecord record, int limit,
                              RecordFilter filter) {
        if (!filter.accepts(record)) {
            return;
        }
        if (newest.size() < limit) {
            newest.add(record);
        } else if (record.getTimeMillis() > newest.peek().getTimeMillis()) {
            newest.poll();
            newest.add(record);
        }
    }

    private static LocalDate utcDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** The source, time and type a record must have to be returned. */
    private static final class RecordFilter {
        private final String source;
        private final Long sinceMillis;
        private final String type;

        RecordFilter(String source, Long sinceMillis, String type) {
            this.source = source;
            this.sinceMillis = sinceMillis;
            this.type = type;
        }

        boolean accepts(JournalRecord record) {
            boolean typeFits = type == null
                ? !JournalRecord.STATS_TYPE.equals(record.getType()) : type.equals(record.getType());
            return typeFits && (source == null || source.equals(record.getSource()))
                && (sinceMillis == null || record.getTimeMillis() >= sinceMillis);
        }
    }
}
