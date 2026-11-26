package org.pnfolder.pnlibrary.core.events.inspect.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventHour;

class EventHistoryCountingTest {
    private final ZoneId zone = ZoneId.of("Europe/Moscow");

    /** The counting as it was before: two tree maps behind one lock, a fresh local time for every call. */
    private static final class OldCounting {
        private final int retentionDays;
        private final TreeMap<java.time.LocalDate, Map<String, long[]>> days = new TreeMap<>();
        private final TreeMap<LocalDateTime, Map<String, long[]>> hours = new TreeMap<>();

        OldCounting(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        void record(String eventType, boolean denied, LocalDateTime now) {
            java.time.LocalDate today = now.toLocalDate();
            synchronized (days) {
                bump(days.computeIfAbsent(today, key -> new HashMap<>()), eventType, denied);
                bump(hours.computeIfAbsent(now.truncatedTo(ChronoUnit.HOURS), key -> new HashMap<>()), eventType, denied);
                long keep = Math.min(retentionDays, 30) * 24L;
                hours.headMap(now.truncatedTo(ChronoUnit.HOURS).minusHours(keep - 1)).clear();
                java.time.LocalDate limit = today.minusDays(retentionDays - 1L);
                if (days.firstKey().isBefore(limit)) {
                    days.headMap(limit).clear();
                }
            }
        }

        private static void bump(Map<String, long[]> counts, String eventType, boolean denied) {
            long[] pair = counts.computeIfAbsent(eventType, key -> new long[2]);
            pair[0]++;
            if (denied) {
                pair[1]++;
            }
        }

        List<String> dayTotals() {
            return totals(days);
        }

        List<String> hourTotals() {
            return totals(hours);
        }

        private static <K> List<String> totals(TreeMap<K, Map<String, long[]>> source) {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<K, Map<String, long[]>> period : source.entrySet()) {
                for (Map.Entry<String, long[]> event : period.getValue().entrySet()) {
                    lines.add(period.getKey() + " " + event.getKey() + " " + event.getValue()[0] + " " + event.getValue()[1]);
                }
            }
            Collections.sort(lines);
            return lines;
        }
    }

    private EventHistory newHistory(Path file, LongSupplier clockMillis) {
        return new EventHistory(file, 30, () -> zone, System::currentTimeMillis, Long.MAX_VALUE, error -> { }, clockMillis);
    }

    @Test
    void sumsPerDayAndHourEqualTheOldCountingAcrossHourDayAndRetentionChanges() {
        long start = Instant.parse("2026-11-01T20:00:00Z").toEpochMilli();
        AtomicLong millis = new AtomicLong(start);
        EventHistory fresh = newHistory(null, millis::get);
        OldCounting old = new OldCounting(30);
        List<String> types = Arrays.asList("a.Buy", "a.Join", "b.Quit", "c.Move");
        // 50 days in steps of 7 minutes 13 seconds so hours, days and the retention edge are all crossed
        int index = 0;
        while (millis.get() < start + 50L * 86_400_000) {
            String type = types.get(index % types.size());
            boolean denied = index % 5 == 0;
            fresh.record(type, denied);
            old.record(type, denied, LocalDateTime.ofInstant(Instant.ofEpochMilli(millis.get()), zone));
            millis.addAndGet(433_000);
            index++;
        }
        List<String> days = new ArrayList<>();
        for (EventDay day : fresh.entries()) {
            days.add(day.getDate() + " " + day.getEventType() + " " + day.getCalls() + " " + day.getDenied());
        }
        List<String> hours = new ArrayList<>();
        for (EventHour hour : fresh.hourly()) {
            hours.add(hour.getHour() + " " + hour.getEventType() + " " + hour.getCalls() + " " + hour.getDenied());
        }
        Collections.sort(days);
        Collections.sort(hours);
        assertEquals(old.dayTotals(), days);
        assertEquals(old.hourTotals(), hours);
        assertEquals(30, new java.util.HashSet<java.time.LocalDate>(datesOf(fresh)).size());
    }

    private static List<java.time.LocalDate> datesOf(EventHistory history) {
        List<java.time.LocalDate> dates = new ArrayList<>();
        for (EventDay day : history.entries()) {
            dates.add(day.getDate());
        }
        return dates;
    }

    @Test
    void theFileKeepsItsFormatAndLoadsBackToTheSameSums(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("history.tsv");
        long millis = Instant.parse("2026-11-09T10:15:00Z").toEpochMilli();
        EventHistory history = newHistory(file, () -> millis);
        for (int call = 0; call < 3; call++) {
            history.record("a.Buy", call == 0);
        }
        history.record("a.Join", false);
        history.close();

        List<String> lines = Files.readAllLines(file);
        assertEquals("# pnLibrary event history v1", lines.get(0));
        List<String> body = new ArrayList<>(lines.subList(1, lines.size()));
        Collections.sort(body);
        assertEquals(Arrays.asList("2026-11-09\ta.Buy\t3\t1", "2026-11-09\ta.Join\t1\t0",
                "2026-11-09T13:00\ta.Buy\t3\t1", "2026-11-09T13:00\ta.Join\t1\t0"), body);
        EventHistory loaded = newHistory(file, () -> millis);
        assertEquals(3L, loaded.entries("a.Buy").get(0).getCalls());
        assertEquals(1L, loaded.hourly("a.Join").get(0).getCalls());
    }

    @Test
    void countingTakesNoLockAndSeveralThreadsLoseNoCall() throws Exception {
        EventHistory history = new EventHistory(null, 30);
        OldCounting old = new OldCounting(30);
        int threads = 8;
        int perThread = 40_000;

        history.record("a.Buy", false); // creates the map entries, which is the only part that locks
        old.record("a.Buy", false, LocalDateTime.now());
        long blockedNew = blockedWhile(threads, perThread, n -> history.record("a.Buy", n == 0));
        long blockedOld = blockedWhile(threads, perThread, n -> old.record("a.Buy", n == 0, LocalDateTime.now()));

        EventDay total = history.entries("a.Buy").get(0);
        assertEquals((long) threads * perThread + 1, total.getCalls());
        assertEquals((long) perThread, total.getDenied());
        // a monitor per call would be thousands; a bucket change once a minute may block a thread or two
        assertTrue(blockedNew <= threads, "threads blocked on a monitor while counting: " + blockedNew + " (old counting: " + blockedOld + ")");
    }

    private static long blockedWhile(int threads, int perThread, IntConsumer work) throws InterruptedException {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        AtomicLong blocked = new AtomicLong();
        List<Thread> workers = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            int number = index;
            Thread worker = new Thread(() -> {
                for (int call = 0; call < perThread; call++) {
                    work.accept(number);
                }
                blocked.addAndGet(bean.getThreadInfo(Thread.currentThread().getId()).getBlockedCount());
            });
            worker.start();
            workers.add(worker);
        }
        for (Thread worker : workers) {
            worker.join();
        }
        return blocked.get();
    }

    @Test
    void aCallAllocatesAtMostHalfOfWhatTheOldCountingDid() {
        EventHistory history = new EventHistory(null, 30);
        OldCounting old = new OldCounting(30);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId();
        for (int warm = 0; warm < 50_000; warm++) {
            history.record("a.Buy", false);
            old.record("a.Buy", false, LocalDateTime.now());
        }

        long before = bean.getThreadAllocatedBytes(id);
        for (int call = 0; call < 200_000; call++) {
            history.record("a.Buy", call % 3 == 0);
        }
        long fresh = bean.getThreadAllocatedBytes(id) - before;
        long middle = bean.getThreadAllocatedBytes(id);
        for (int call = 0; call < 200_000; call++) {
            old.record("a.Buy", call % 3 == 0, LocalDateTime.now());
        }
        long previous = bean.getThreadAllocatedBytes(id) - middle;

        assertTrue(fresh * 2 <= previous, "new " + fresh + " bytes, old " + previous + " bytes for 200000 calls");
    }

    @Test
    void theZoneOfALaterMinuteIsHonoured() {
        AtomicLong millis = new AtomicLong(Instant.parse("2026-11-09T21:59:30Z").toEpochMilli());
        EventHistory history = new EventHistory(null, 30, () -> ZoneOffset.UTC, System::currentTimeMillis, 60_000L,
                error -> { }, millis::get);
        history.record("a.Buy", false);
        millis.addAndGet(60_000);
        history.record("a.Buy", false);

        List<LocalDateTime> hours = new ArrayList<>();
        for (EventHour hour : history.hourly()) {
            hours.add(hour.getHour());
        }
        assertEquals(Arrays.asList(LocalDateTime.parse("2026-11-09T22:00"), LocalDateTime.parse("2026-11-09T21:00")), hours);
    }
}
