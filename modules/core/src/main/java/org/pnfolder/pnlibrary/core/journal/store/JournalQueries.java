package org.pnfolder.pnlibrary.core.journal.store;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.journal.JournalDetail;
import org.pnfolder.pnlibrary.core.journal.JournalGroup;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;
import org.pnfolder.pnlibrary.core.journal.view.JournalDigest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** What the menu and the join notice read from the journal: equal events merged, one event with its details. */
public final class JournalQueries {
    private static final int SCAN = 5_000;
    private static final int TIMES = 50;
    private static final long NOTICE_TTL_MILLIS = 45_000L;

    private final JournalStore journal;
    private final PnLibraryConfig config;
    private final long startedAtMillis;
    private final String eventsCommand;
    private final LongSupplier clock;
    private final Object noticeLock = new Object();
    private Long noticeBuiltAt;
    private List<Component> notice = Collections.emptyList();

    public JournalQueries(@NotNull JournalStore journal, @NotNull PnLibraryConfig config, long startedAtMillis,
                          @NotNull String eventsCommand) {
        this(journal, config, startedAtMillis, eventsCommand, System::currentTimeMillis);
    }

    public JournalQueries(@NotNull JournalStore journal, @NotNull PnLibraryConfig config, long startedAtMillis,
                          @NotNull String eventsCommand, @NotNull LongSupplier clock) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.config = Objects.requireNonNull(config, "config");
        this.startedAtMillis = startedAtMillis;
        this.eventsCommand = Objects.requireNonNull(eventsCommand, "eventsCommand");
        this.clock = clock;
    }

    /**
     * The lines shown to an administrator who joins, empty when the journal cannot be read. Several joins within
     * {@value #NOTICE_TTL_MILLIS} milliseconds share one journal read, so a wave of administrators does not rescan
     * the files.
     */
    public @NotNull List<Component> joinNotice() {
        synchronized (noticeLock) {
            long now = clock.getAsLong();
            Long builtAt = noticeBuiltAt;
            if (builtAt == null || now - builtAt >= NOTICE_TTL_MILLIS || now < builtAt) {
                notice = buildJoinNotice(now);
                noticeBuiltAt = now;
            }
            return notice;
        }
    }

    private List<Component> buildJoinNotice(long now) {
        int hours = config.getJournalJoinNoticeHours();
        long since = now - Duration.ofHours(hours).toMillis();
        return Failures.runCatchingNonFatal(() -> JournalDigest.join(
            journal.read(Collections.emptySet(), null, since, SCAN, null), now, hours, startedAtMillis,
            eventsCommand)).getOrDefault(Collections.emptyList());
    }

    /** Events of the last {@code sinceHours} hours, equal ones merged, newest first; {@code level} keeps one group of levels. */
    public @NotNull List<JournalGroup> groups(@Nullable String level, int sinceHours, int limit) {
        long since = System.currentTimeMillis() - Duration.ofHours(sinceHours).toMillis();
        Set<JournalLevel> levels = EnumSet.noneOf(JournalLevel.class);
        for (JournalLevel candidate : JournalLevel.values()) {
            if (level == null || candidate.getFileGroup().equals(level)) {
                levels.add(candidate);
            }
        }
        return Failures.runCatchingNonFatal(() -> mergeEqual(journal.read(levels, null, since, SCAN, null), limit))
            .getOrDefault(Collections.emptyList());
    }

    private static List<JournalGroup> mergeEqual(List<JournalRecord> records, int limit) {
        Map<String, List<JournalRecord>> byId = new LinkedHashMap<>();
        for (JournalRecord record : records) {
            List<JournalRecord> group = byId.get(record.getFingerprint());
            if (group == null) {
                group = new ArrayList<>();
                byId.put(record.getFingerprint(), group);
            }
            group.add(record);
        }
        List<JournalGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<JournalRecord>> entry : byId.entrySet()) {
            JournalRecord newest = newestOf(entry.getValue());
            groups.add(new JournalGroup(entry.getKey(), newest.getLevel().getFileGroup(), newest.getSource(),
                newest.getFirstLine(), entry.getValue().size(), newest.getTimeMillis()));
        }
        groups.sort((left, right) -> Long.compare(right.getLastMillis(), left.getLastMillis()));
        return groups.size() > limit ? new ArrayList<>(groups.subList(0, limit)) : groups;
    }

    /** The newest record of the event {@code id} with every time it happened, or null when it left the journal. */
    public @Nullable JournalDetail detail(@NotNull String id) {
        return Failures.runCatchingNonFatal(() -> findDetail(id)).getOrNull();
    }

    private JournalDetail findDetail(String id) {
        List<JournalRecord> records = new ArrayList<>();
        for (JournalRecord record : journal.read(SCAN)) {
            if (record.getFingerprint().equals(id)) {
                records.add(record);
            }
        }
        if (records.isEmpty()) {
            return null;
        }
        JournalRecord newest = newestOf(records);
        Map<String, String> details = new LinkedHashMap<>(newest.getDetails());
        details.remove(JournalRecord.STACKTRACE);
        List<Long> times = new ArrayList<>();
        for (JournalRecord record : records) {
            times.add(record.getTimeMillis());
        }
        times.sort(Collections.reverseOrder());
        return new JournalDetail(id, newest.getLevel().getFileGroup(), newest.getSource(), newest.getMessage(),
            newest.getDetails().get(JournalRecord.STACKTRACE), details,
            new ArrayList<>(times.subList(0, Math.min(TIMES, times.size()))));
    }

    private static JournalRecord newestOf(List<JournalRecord> records) {
        JournalRecord newest = records.get(0);
        for (JournalRecord record : records) {
            if (record.getTimeMillis() > newest.getTimeMillis()) {
                newest = record;
            }
        }
        return newest;
    }
}
