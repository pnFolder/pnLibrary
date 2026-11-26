package org.pnfolder.pnlibrary.core.events.inspect.history;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventHour;
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle;
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Per-day call counters of every event type, kept for the retention days and stored in a small text file so the
 * history survives restarts.
 *
 * <p>Counting takes no lock: the current hour and day are looked up once a minute, and each call is a map read and
 * two adder increments. Writing happens on a background worker at most once per flush interval and once more on
 * {@link #close()}, so a crash loses at most a minute of counts. A retention of {@code 0} keeps nothing. Hourly
 * counters of the last 30 days at most are stored in the same file (see {@link EventHistoryFile}).
 */
public final class EventHistory implements AutoCloseable {
    private static final int MAX_HOURLY_DAYS = 30;
    private static final long MINUTE_MILLIS = 60_000L;

    private final Path file;
    private final int retentionDays;
    private final Supplier<ZoneId> zone;
    private final LongSupplier clock;
    private final long flushEveryMillis;
    private final Consumer<Throwable> onFailure;
    private final LongSupplier now;
    private final ConcurrentSkipListMap<LocalDate, ConcurrentHashMap<String, EventCounts>> days =
            new ConcurrentSkipListMap<LocalDate, ConcurrentHashMap<String, EventCounts>>();
    private final ConcurrentSkipListMap<LocalDateTime, ConcurrentHashMap<String, EventCounts>> hours =
            new ConcurrentSkipListMap<LocalDateTime, ConcurrentHashMap<String, EventCounts>>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicLong lastFlush;
    private final FailureThrottle failureThrottle = new FailureThrottle();
    private final ManagedWorker worker;
    private volatile HistoryBucket bucket;

    /** A history in {@code file} (none when {@code null}) that keeps {@code retentionDays} days. */
    public EventHistory(@Nullable Path file, int retentionDays) {
        this(file, retentionDays, ZoneId::systemDefault, error -> { });
    }

    /** A history that decides the hour and day in the {@code zone} and passes unreadable or unwritable files to {@code onFailure}. */
    public EventHistory(
            @Nullable Path file,
            int retentionDays,
            @NotNull Supplier<ZoneId> zone,
            @NotNull Consumer<Throwable> onFailure) {
        this(file, retentionDays, zone, System::currentTimeMillis, 60_000L, onFailure, System::currentTimeMillis);
    }

    /**
     * A history with every part chosen: {@code clock} paces the writes, every {@code flushEveryMillis}, and
     * {@code now} is the wall-clock time that decides which hour and day a call belongs to.
     */
    public EventHistory(
            @Nullable Path file,
            int retentionDays,
            @NotNull Supplier<ZoneId> zone,
            @NotNull LongSupplier clock,
            long flushEveryMillis,
            @NotNull Consumer<Throwable> onFailure,
            @NotNull LongSupplier now) {
        this.file = file;
        this.retentionDays = retentionDays;
        this.zone = zone;
        this.clock = clock;
        this.flushEveryMillis = flushEveryMillis;
        this.onFailure = onFailure;
        this.now = now;
        this.lastFlush = new AtomicLong(clock.getAsLong());
        this.worker = file != null && retentionDays > 0 ? new ManagedWorker("pnLibrary-event-history") : null;
        if (file != null && retentionDays > 0) {
            loadFrom(file);
        }
    }

    /** How many days are kept; {@code 0} means the history is off. */
    public int getRetention() {
        return retentionDays;
    }

    /** Counts one call of {@code eventType} for today and this hour, and writes the file later if enough time passed. */
    public void record(@NotNull String eventType, boolean denied) {
        if (retentionDays <= 0) {
            return;
        }
        HistoryBucket current = currentBucket();
        long deniedAmount = denied ? 1L : 0L;
        countsOf(current.getDayCounts(), eventType).add(1, deniedAmount);
        countsOf(current.getHourCounts(), eventType).add(1, deniedAmount);
        if (!dirty.get()) {
            dirty.set(true);
        }
        flushIfDue();
    }

    private void flushIfDue() {
        long tick = clock.getAsLong();
        long previous = lastFlush.get();
        if (tick - previous >= flushEveryMillis && lastFlush.compareAndSet(previous, tick) && worker != null) {
            Failures.runCatchingNonFatal(() -> worker.submit(() -> {
                flush();
                return null;
            }));
        }
    }

    private static EventCounts countsOf(ConcurrentHashMap<String, EventCounts> map, String eventType) {
        EventCounts counts = map.get(eventType);
        return counts != null ? counts : map.computeIfAbsent(eventType, key -> new EventCounts());
    }

    private HistoryBucket currentBucket() {
        long millis = now.getAsLong();
        HistoryBucket current = bucket;
        return current != null && current.covers(millis) ? current : startBucket(millis);
    }

    // Offsets of time zones are whole minutes, so a bucket that ends on a minute boundary never straddles an hour.
    private HistoryBucket startBucket(long millis) {
        LocalDateTime local = localTime(millis);
        long minuteStart = millis - Math.floorMod(millis, MINUTE_MILLIS);
        HistoryBucket created = new HistoryBucket(
                minuteStart,
                minuteStart + MINUTE_MILLIS,
                hours.computeIfAbsent(local.truncatedTo(ChronoUnit.HOURS), key -> new ConcurrentHashMap<String, EventCounts>()),
                days.computeIfAbsent(local.toLocalDate(), key -> new ConcurrentHashMap<String, EventCounts>()));
        bucket = created;
        prune(local);
        return created;
    }

    private LocalDateTime localTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone.get());
    }

    /** The recorded days, newest first and then by event name. */
    @NotNull
    public List<EventDay> entries() {
        return entries(null);
    }

    /** The recorded days, newest first and then by event name; only {@code eventType} when given. */
    @NotNull
    public List<EventDay> entries(@Nullable String eventType) {
        List<EventDay> found = new ArrayList<EventDay>();
        for (Map.Entry<LocalDate, ConcurrentHashMap<String, EventCounts>> day : days.descendingMap().entrySet()) {
            for (Map.Entry<String, EventCounts> event : new TreeMap<String, EventCounts>(day.getValue()).entrySet()) {
                if (eventType == null || event.getKey().equals(eventType)) {
                    found.add(new EventDay(day.getKey(), event.getKey(), event.getValue().getCalls(), event.getValue().getDenied()));
                }
            }
        }
        return found;
    }

    /** The recorded hours, newest first and then by event name. */
    @NotNull
    public List<EventHour> hourly() {
        return hourly(null);
    }

    /** The recorded hours, newest first and then by event name; only {@code eventType} when given. */
    @NotNull
    public List<EventHour> hourly(@Nullable String eventType) {
        List<EventHour> found = new ArrayList<EventHour>();
        for (Map.Entry<LocalDateTime, ConcurrentHashMap<String, EventCounts>> hour : hours.descendingMap().entrySet()) {
            for (Map.Entry<String, EventCounts> event : new TreeMap<String, EventCounts>(hour.getValue()).entrySet()) {
                if (eventType == null || event.getKey().equals(eventType)) {
                    found.add(new EventHour(hour.getKey(), event.getKey(), event.getValue().getCalls(), event.getValue().getDenied()));
                }
            }
        }
        return found;
    }

    /** Writes days and hours to {@code event-history.csv} beside the history file; {@code null} if not possible. */
    @Nullable
    public Path exportCsv() {
        return export("event-history.csv", EventHistoryExport::csv);
    }

    /** Writes days and hours to {@code event-history.json} beside the history file; {@code null} if not possible. */
    @Nullable
    public Path exportJson() {
        return export("event-history.json", EventHistoryExport::json);
    }

    private Path export(String name, BiFunction<List<EventDay>, List<EventHour>, String> render) {
        if (file == null || retentionDays <= 0) {
            return null;
        }
        Path target = file.resolveSibling(name);
        byte[] bytes = render.apply(entries(), hourly()).getBytes(StandardCharsets.UTF_8);
        return Failures.runCatchingNonFatal(() -> {
            AtomicFiles.write(target, bytes);
            return target;
        }).getOrNull();
    }

    private void prune(LocalDateTime local) {
        long keepHours = Math.min(retentionDays, MAX_HOURLY_DAYS) * 24L;
        hours.headMap(local.truncatedTo(ChronoUnit.HOURS).minusHours(keepHours - 1)).clear();
        days.headMap(local.toLocalDate().minusDays(retentionDays - 1L)).clear();
    }

    private void loadFrom(Path source) {
        EventHistoryFile.Sink sink = new EventHistoryFile.Sink() {
            @Override
            public void day(LocalDate date, String event, long calls, long denied) {
                countsOf(days.computeIfAbsent(date, key -> new ConcurrentHashMap<String, EventCounts>()), event).add(calls, denied);
            }

            @Override
            public void hour(LocalDateTime hour, String event, long calls, long denied) {
                countsOf(hours.computeIfAbsent(hour, key -> new ConcurrentHashMap<String, EventCounts>()), event).add(calls, denied);
            }
        };
        Failures.runCatchingNonFatal((Callable<Void>) () -> {
            EventHistoryFile.read(source, sink);
            prune(localTime(now.getAsLong()));
            return null;
        }).onFailure(this::report);
    }

    /** Writes the file now when something changed; the write goes to a temporary file first. */
    public void flush() {
        Path target = file;
        if (target == null || retentionDays <= 0 || !dirty.getAndSet(false)) {
            return;
        }
        prune(localTime(now.getAsLong()));
        byte[] bytes = EventHistoryFile.render(days, hours).getBytes(StandardCharsets.UTF_8);
        Failures.runCatchingNonFatal((Callable<Void>) () -> write(target, bytes)).onFailure(error -> {
            dirty.set(true);
            report(error);
        });
    }

    private static Void write(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        AtomicFiles.write(target, bytes);
        return null;
    }

    /** A history that cannot be read or written is lost count by count, so the first failure of each kind is passed on. */
    private void report(Throwable error) {
        if (failureThrottle.shouldReport(error.getClass().getName())) {
            onFailure.accept(error);
        }
    }

    @Override
    public void close() {
        if (worker != null) {
            worker.close();
        }
        flush();
    }
}
