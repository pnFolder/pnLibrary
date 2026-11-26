package org.pnfolder.pnlibrary.core.journal.store;

import org.pnfolder.pnlibrary.core.journal.JournalLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Removes journal files that outlived their level's retention or do not fit the disk budget. */
final class JournalRetention {
    // Disk budget is checked at least this often even when no new segment starts.
    private static final long ENFORCE_INTERVAL_MILLIS = 60_000L;

    private final Path directory;
    private final JournalFiles files;
    private final Map<JournalLevel, Duration> retention;
    private final Clock clock;
    private final long maximumDiskBytes;
    private final Runnable forgetSegments;
    private long lastEnforcedAtMillis;
    private boolean newSegmentStarted;

    JournalRetention(Path directory, JournalFiles files, Map<JournalLevel, Duration> retention, Clock clock,
                     long maximumDiskBytes, Runnable forgetSegments) {
        this.directory = directory;
        this.files = files;
        this.retention = retention;
        this.clock = clock;
        this.maximumDiskBytes = maximumDiskBytes;
        this.forgetSegments = forgetSegments;
        this.lastEnforcedAtMillis = clock.millis();
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    /** A new segment triggers a disk-budget check to avoid accumulating too many files. */
    void segmentStarted() {
        newSegmentStarted = true;
    }

    boolean enforcementDue() {
        return newSegmentStarted || clock.millis() - lastEnforcedAtMillis >= ENFORCE_INTERVAL_MILLIS;
    }

    long retentionDays(JournalLevel level) {
        return Math.max(retention.get(level).toDays(), 1);
    }

    /** Deletes files older than their level's retention and enforces the disk budget; returns how many went. */
    int prune() throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        LocalDate today = today();
        List<Path> survivors = new ArrayList<>();
        int removed = 0;
        for (Path file : files.all()) {
            JournalLevel level = files.levelOf(file);
            LocalDate date = files.dateOf(file);
            if (level == null || date == null || !date.isBefore(today.minusDays(retentionDays(level)))) {
                survivors.add(file);
            } else {
                Files.deleteIfExists(file);
                removed++;
            }
        }
        if (removed > 0) {
            forgetSegments.run();
        }
        return removed + enforce(survivors);
    }

    // Routine levels evicted before ERROR: the disk budget must never force-remove error records early.
    int enforce(List<Path> all) throws IOException {
        lastEnforcedAtMillis = clock.millis();
        newSegmentStarted = false;
        List<Path> ordered = new ArrayList<>(all);
        ordered.sort(evictionOrder());
        long total = 0;
        for (Path file : ordered) {
            total += Files.size(file);
        }
        int removed = 0;
        for (Path file : ordered) {
            if (total <= maximumDiskBytes) {
                break;
            }
            long bytes = Files.size(file);
            if (Files.deleteIfExists(file)) {
                total -= bytes;
                removed++;
            }
        }
        if (removed > 0) {
            forgetSegments.run();
        }
        return removed;
    }

    private Comparator<Path> evictionOrder() {
        String errorPrefix = JournalLevel.ERROR.getFileGroup() + "-";
        return (left, right) -> {
            int byLevel = Boolean.compare(startsWith(left, errorPrefix), startsWith(right, errorPrefix));
            if (byLevel != 0) {
                return byLevel;
            }
            LocalDate leftDate = files.dateOf(left);
            LocalDate rightDate = files.dateOf(right);
            int byDate = leftDate == null || rightDate == null
                ? (leftDate == rightDate ? 0 : leftDate == null ? -1 : 1) : leftDate.compareTo(rightDate);
            return byDate != 0 ? byDate : Integer.compare(files.segmentOf(left), files.segmentOf(right));
        };
    }

    private static boolean startsWith(Path file, String prefix) {
        return file.getFileName().toString().startsWith(prefix);
    }
}
