package org.pnfolder.pnlibrary.core.stats;

import java.time.Duration;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;

/**
 * Writes a {@link StatsSnapshot} into the journal every hour and once more at shutdown, so the black box keeps how the
 * server was doing, and reads the stored ones back for {@code /pn stats}.
 */
public final class StatsRecorder implements AutoCloseable {
    private static final Duration EVERY = Duration.ofHours(1);

    private final StatsCollector collector;
    private final JournalStore journal;
    private final Consumer<Throwable> onFailure;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "pnLibrary-stats");
        thread.setDaemon(true);
        return thread;
    });

    public StatsRecorder(@NotNull StatsCollector collector, @NotNull JournalStore journal) {
        this(collector, journal, error -> { });
    }

    public StatsRecorder(@NotNull StatsCollector collector, @NotNull JournalStore journal,
                         @NotNull Consumer<Throwable> onFailure) {
        this.collector = collector;
        this.journal = journal;
        this.onFailure = onFailure;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::record, EVERY.toMinutes(), EVERY.toMinutes(), TimeUnit.MINUTES);
    }

    /** Stores the current numbers; a failure goes to the failure handler and never to the caller, so statistics cannot disturb the server. */
    public void record() {
        Failures.runCatchingNonFatal(() -> {
            StatsSnapshot snapshot = collector.collect();
            journal.append(new JournalRecord(
                snapshot.getTimeMillis(),
                JournalLevel.INFO,
                PnLibraryIdentity.NAME.toLowerCase(Locale.ROOT),
                StatsSnapshot.RECORD_TYPE,
                "Статистика: задач " + snapshot.getTasksActive() + ", ошибок за сутки " + snapshot.getErrors24h(),
                snapshot.toDetails()));
            return null;
        }).onFailure(onFailure);
    }

    /** The stored snapshots of the last {@code days} days, newest first. */
    public @NotNull List<StatsSnapshot> history(long days, int limit) {
        long since = System.currentTimeMillis() - Duration.ofDays(days).toMillis();
        List<StatsSnapshot> result = new ArrayList<StatsSnapshot>();
        for (JournalRecord record : journal.read(Collections.singleton(JournalLevel.INFO), null, since, limit,
            StatsSnapshot.RECORD_TYPE)) {
            StatsSnapshot snapshot = StatsSnapshot.fromDetails(record.getTimeMillis(), record.getDetails());
            if (snapshot != null) {
                result.add(snapshot);
            }
        }
        return result;
    }

    @Override
    public void close() {
        executor.shutdownNow();
        record();
    }
}
