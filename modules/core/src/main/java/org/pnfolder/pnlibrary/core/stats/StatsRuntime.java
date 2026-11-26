package org.pnfolder.pnlibrary.core.stats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;

/** The statistics of the server as one part: taking a snapshot, keeping hourly ones in the journal, and {@code /pn stats}. */
public final class StatsRuntime implements AutoCloseable {
    private static final long FRESH_MILLIS = 5_000L;

    private final StatsCollector collector;
    private final StatsRecorder recorder;
    private volatile StatsSnapshot last;

    public StatsRuntime(@NotNull Supplier<List<TaskSnapshot>> tasks, @NotNull Supplier<List<EventInfo>> events,
                        @NotNull JournalStore journal) {
        this(tasks, events, journal, error -> { });
    }

    public StatsRuntime(@NotNull Supplier<List<TaskSnapshot>> tasks, @NotNull Supplier<List<EventInfo>> events,
                        @NotNull JournalStore journal, @NotNull Consumer<Throwable> onFailure) {
        this.collector = new StatsCollector(tasks, events, journal);
        this.recorder = new StatsRecorder(collector, journal, onFailure);
    }

    /**
     * The numbers now. Taking them reads the journal files, so a snapshot younger than five seconds is reused: one
     * open menu or a repeated command must not read the disk again each time.
     */
    public @NotNull StatsSnapshot snapshot() {
        StatsSnapshot recent = last;
        if (recent != null && System.currentTimeMillis() - recent.getTimeMillis() < FRESH_MILLIS) {
            return recent;
        }
        StatsSnapshot fresh = collector.collect();
        last = fresh;
        return fresh;
    }

    /** The numbers as {@code pnlibrary} placeholders see them. */
    public @NotNull Map<String, Long> values() {
        StatsSnapshot now = snapshot();
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("errors", (long) now.getErrors24h());
        values.put("warnings", (long) now.getWarnings24h());
        values.put("tasks", (long) now.getTasksActive());
        values.put("tasks.failed", (long) now.getTasksFailed());
        values.put("events", now.getEventsCalled());
        return values;
    }

    public @NotNull CommandDefinition command() {
        return StatsCommand.create(this::snapshot, recorder);
    }

    public void start() {
        recorder.start();
    }

    @Override
    public void close() {
        recorder.close();
    }
}
