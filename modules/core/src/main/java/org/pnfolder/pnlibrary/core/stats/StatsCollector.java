package org.pnfolder.pnlibrary.core.stats;

import java.util.Objects;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.pnfolder.pnlibrary.common.lang.Pair;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;

/** Takes a {@link StatsSnapshot} from the task list, the event counters and the journal. */
public final class StatsCollector {
    private static final Duration WINDOW = Duration.ofHours(24);
    /** Upper bound for one read; a count that reaches it means "at least this many". */
    private static final int READ_LIMIT = 5_000;
    private static final int TOP_SOURCES = 5;

    private final Supplier<List<TaskSnapshot>> tasks;
    private final Supplier<List<EventInfo>> events;
    private final JournalStore journal;
    private final LongSupplier clock;

    public StatsCollector(@NotNull Supplier<List<TaskSnapshot>> tasks, @NotNull Supplier<List<EventInfo>> events,
                          @NotNull JournalStore journal) {
        this(tasks, events, journal, System::currentTimeMillis);
    }

    public StatsCollector(@NotNull Supplier<List<TaskSnapshot>> tasks, @NotNull Supplier<List<EventInfo>> events,
                          @NotNull JournalStore journal, @NotNull LongSupplier clock) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.events = Objects.requireNonNull(events, "events");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.clock = clock;
    }

    public @NotNull StatsSnapshot collect() {
        long now = clock.getAsLong();
        List<TaskSnapshot> taskList = tasks.get();
        List<EventInfo> eventList = events.get();
        long since = now - WINDOW.toMillis();
        List<JournalRecord> warnings = read(JournalLevel.WARNING, since);
        List<JournalRecord> errors = read(JournalLevel.ERROR, since);
        int active = 0;
        int failed = 0;
        long runs = 0;
        long skipped = 0;
        for (TaskSnapshot task : taskList) {
            if (task.getStatus() == TaskStatus.SCHEDULED || task.getStatus() == TaskStatus.RUNNING) {
                active++;
            }
            if (task.getStatus() == TaskStatus.FAILED) {
                failed++;
            }
            runs += task.getRunCount();
            skipped += task.getSkippedCount();
        }
        long called = 0;
        long denied = 0;
        for (EventInfo event : eventList) {
            called += event.getCalls();
            denied += event.getDenied();
        }
        return new StatsSnapshot(now, active, failed, runs, skipped, called, denied, warnings.size(), errors.size(),
            topSources(errors));
    }

    private List<JournalRecord> read(JournalLevel level, long since) {
        return journal.read(Collections.singleton(level), null, since, READ_LIMIT, null);
    }

    private static List<Pair<String, Integer>> topSources(List<JournalRecord> errors) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (JournalRecord error : errors) {
            Integer count = counts.get(error.getSource());
            counts.put(error.getSource(), count == null ? 1 : count + 1);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        List<Pair<String, Integer>> top = new ArrayList<Pair<String, Integer>>();
        for (Map.Entry<String, Integer> entry : entries.subList(0, Math.min(TOP_SOURCES, entries.size()))) {
            top.add(new Pair<String, Integer>(entry.getKey(), entry.getValue()));
        }
        return top;
    }
}
