package org.pnfolder.pnlibrary.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.TreeMap

class EventHistoryCountingTest {
    private val zone = ZoneId.of("Europe/Moscow")

    /** The counting as it was before: two tree maps behind one lock, a fresh local time for every call. */
    private class Old(private val retentionDays: Int, private val zone: ZoneId) {
        class Counts(var calls: Long = 0, var denied: Long = 0)

        val days = TreeMap<LocalDate, MutableMap<String, Counts>>()
        val hours = TreeMap<LocalDateTime, MutableMap<String, Counts>>()

        fun record(eventType: String, denied: Boolean, now: LocalDateTime = LocalDateTime.now(zone)) {
            val today = now.toLocalDate()
            synchronized(days) {
                val counts = days.getOrPut(today) { HashMap() }.getOrPut(eventType) { Counts() }
                counts.calls++
                if (denied) counts.denied++
                val hourly = hours.getOrPut(now.truncatedTo(ChronoUnit.HOURS)) { HashMap() }.getOrPut(eventType) { Counts() }
                hourly.calls++
                if (denied) hourly.denied++
                val keep = minOf(retentionDays, 30) * 24L
                hours.headMap(now.truncatedTo(ChronoUnit.HOURS).minusHours(keep - 1)).clear()
                if (days.firstKey().isBefore(today.minusDays(retentionDays.toLong() - 1))) days.headMap(today.minusDays(retentionDays.toLong() - 1)).clear()
            }
        }

        fun dayTotals() = days.flatMap { (d, m) -> m.map { Triple("$d ${it.key}", it.value.calls, it.value.denied) } }.sortedBy { it.first }
        fun hourTotals() = hours.flatMap { (d, m) -> m.map { Triple("$d ${it.key}", it.value.calls, it.value.denied) } }.sortedBy { it.first }
    }

    private fun newHistory(file: Path?, clockMillis: () -> Long) =
        EventHistory(file, 30, zone = { zone }, flushEveryMillis = Long.MAX_VALUE, now = clockMillis)

    @Test
    fun `sums per day and hour equal the old counting across hour, day and retention changes`() {
        val start = Instant.parse("2026-11-01T20:00:00Z").toEpochMilli()
        var millis = start
        val fresh = newHistory(null) { millis }
        val old = Old(30, zone)
        val types = listOf("a.Buy", "a.Join", "b.Quit", "c.Move")
        // 50 days in steps of 7 minutes 13 seconds so hours, days and the retention edge are all crossed
        var index = 0
        while (millis < start + 50L * 86_400_000) {
            val type = types[index % types.size]
            val denied = index % 5 == 0
            fresh.record(type, denied)
            old.record(type, denied, LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone))
            millis += 433_000
            index++
        }
        assertEquals(old.dayTotals(), fresh.entries().map { Triple("${it.date} ${it.eventType}", it.calls, it.denied) }.sortedBy { it.first })
        assertEquals(old.hourTotals(), fresh.hourly().map { Triple("${it.hour} ${it.eventType}", it.calls, it.denied) }.sortedBy { it.first })
        assertTrue(fresh.entries().map { it.date }.toSet().size == 30)
    }

    @Test
    fun `the file keeps its format and loads back to the same sums`(@TempDir dir: Path) {
        val file = dir.resolve("history.tsv")
        val millis = Instant.parse("2026-11-09T10:15:00Z").toEpochMilli()
        newHistory(file) { millis }.use { history ->
            repeat(3) { history.record("a.Buy", denied = it == 0) }
            history.record("a.Join", denied = false)
        }
        val lines = Files.readAllLines(file)
        assertEquals("# pnLibrary event history v1", lines.first())
        assertEquals(
            listOf("2026-11-09\ta.Buy\t3\t1", "2026-11-09\ta.Join\t1\t0", "2026-11-09T13:00\ta.Buy\t3\t1", "2026-11-09T13:00\ta.Join\t1\t0"),
            lines.drop(1).sorted(),
        )
        val loaded = newHistory(file) { millis }
        assertEquals(3L, loaded.entries("a.Buy").single().calls)
        assertEquals(1L, loaded.hourly("a.Join").single().calls)
    }

    @Test
    fun `counting takes no lock and several threads lose no call`() {
        val history = EventHistory(null, 30)
        val old = Old(30, ZoneId.systemDefault())
        val threads = 8
        val perThread = 40_000
        val bean = ManagementFactory.getThreadMXBean()

        fun blockedWhile(work: (Int) -> Unit): Long {
            val blocked = java.util.concurrent.atomic.AtomicLong()
            val workers = List(threads) { n ->
                Thread {
                    repeat(perThread) { work(n) }
                    blocked.addAndGet(bean.getThreadInfo(Thread.currentThread().id).blockedCount)
                }.also(Thread::start)
            }
            workers.forEach(Thread::join)
            return blocked.get()
        }

        history.record("a.Buy", false) // creates the map entries, which is the only part that locks
        old.record("a.Buy", false)
        val blockedNew = blockedWhile { history.record("a.Buy", it == 0) }
        val blockedOld = blockedWhile { old.record("a.Buy", it == 0) }

        assertEquals(threads.toLong() * perThread + 1, history.entries("a.Buy").single().calls)
        assertEquals(perThread.toLong(), history.entries("a.Buy").single().denied)
        // a monitor per call would be thousands; a bucket change once a minute may block a thread or two
        assertTrue(blockedNew <= threads, "threads blocked on a monitor while counting: $blockedNew (old counting: $blockedOld)")
    }

    @Test
    fun `a call allocates at most half of what the old counting did`() {
        val history = EventHistory(null, 30)
        val old = Old(30, ZoneId.systemDefault())
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        repeat(50_000) { history.record("a.Buy", false); old.record("a.Buy", false) }

        val before = bean.getThreadAllocatedBytes(id)
        repeat(200_000) { history.record("a.Buy", it % 3 == 0) }
        val fresh = bean.getThreadAllocatedBytes(id) - before
        val middle = bean.getThreadAllocatedBytes(id)
        repeat(200_000) { old.record("a.Buy", it % 3 == 0) }
        val previous = bean.getThreadAllocatedBytes(id) - middle

        assertTrue(fresh * 2 <= previous, "new $fresh bytes, old $previous bytes for 200000 calls")
    }

    @Test
    fun `the zone of a later minute is honoured`() {
        var millis = Instant.parse("2026-11-09T21:59:30Z").toEpochMilli()
        val history = EventHistory(null, 30, zone = { ZoneOffset.UTC }, now = { millis })
        history.record("a.Buy", false)
        millis += 60_000
        history.record("a.Buy", false)

        assertEquals(listOf(LocalDateTime.parse("2026-11-09T22:00"), LocalDateTime.parse("2026-11-09T21:00")), history.hourly().map { it.hour })
    }
}
