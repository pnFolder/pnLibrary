package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.api.events.EventHour
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Per-day call counters of every event type, kept for [retentionDays] and stored in a small text file so the
 * history survives restarts.
 *
 * Counting takes no lock: the current hour and day are looked up once a minute, and each call is a map read and
 * two adder increments. Writing happens on a background worker at most once per [flushEveryMillis]
 * and once more on [close], so a crash loses at most a minute of counts. A retention of `0` keeps nothing.
 * The file starts with a `# pnLibrary event history v1` line and then holds one `date<TAB>event<TAB>calls<TAB>denied`
 * line per day and event; lines that do not fit that shape are ignored. Hourly counters of the last 30 days at most
 * are stored the same way, with `yyyy-MM-ddTHH:00` in the first column.
 */
class EventHistory(
    private val file: Path?,
    private val retentionDays: Int,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val flushEveryMillis: Long = 60_000,
    private val onFailure: (Throwable) -> Unit = {},
    /** The wall-clock time that decides which hour and day a call belongs to. */
    private val now: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private class Counts {
        val calls = LongAdder()
        val denied = LongAdder()

        fun add(amount: Long, deniedAmount: Long) {
            calls.add(amount)
            denied.add(deniedAmount)
        }
    }

    /** The hour and day that calls belong to from [validFrom] until [validUntil]; replaced when the minute changes. */
    private class Bucket(
        val validFrom: Long,
        val validUntil: Long,
        val hourCounts: ConcurrentHashMap<String, Counts>,
        val dayCounts: ConcurrentHashMap<String, Counts>,
    )

    private val days = ConcurrentSkipListMap<LocalDate, ConcurrentHashMap<String, Counts>>()
    private val hours = ConcurrentSkipListMap<LocalDateTime, ConcurrentHashMap<String, Counts>>()
    private val dirty = AtomicBoolean(false)
    private val lastFlush = AtomicLong(clock())
    private val failureThrottle = FailureThrottle()
    private val worker = if (file != null && retentionDays > 0) ManagedWorker("pnLibrary-event-history") else null

    @Volatile
    private var bucket: Bucket? = null

    /** How many days are kept; `0` means the history is off. */
    val retention: Int get() = retentionDays

    init {
        if (file != null && retentionDays > 0) runCatchingNonFatal { load(file) }.onFailure(::report)
    }

    /** Counts one call of [eventType] for today and this hour, and writes the file later if enough time passed. */
    fun record(eventType: String, denied: Boolean) {
        if (retentionDays <= 0) return
        val current = currentBucket()
        val deniedAmount = if (denied) 1L else 0L
        countsOf(current.dayCounts, eventType).add(1, deniedAmount)
        countsOf(current.hourCounts, eventType).add(1, deniedAmount)
        if (!dirty.get()) dirty.set(true)
        val tick = clock()
        val previous = lastFlush.get()
        if (tick - previous >= flushEveryMillis && lastFlush.compareAndSet(previous, tick)) {
            worker?.let { runCatchingNonFatal { it.submit { flush() } } }
        }
    }

    private fun countsOf(map: ConcurrentHashMap<String, Counts>, eventType: String): Counts =
        map[eventType] ?: map.computeIfAbsent(eventType) { Counts() }

    private fun currentBucket(): Bucket {
        val millis = now()
        bucket?.let { if (millis >= it.validFrom && millis < it.validUntil) return it }
        return startBucket(millis)
    }

    // Offsets of time zones are whole minutes, so a bucket that ends on a minute boundary never straddles an hour.
    private fun startBucket(millis: Long): Bucket {
        val local = localTime(millis)
        val minuteStart = millis - Math.floorMod(millis, MINUTE_MILLIS)
        val created = Bucket(
            minuteStart,
            minuteStart + MINUTE_MILLIS,
            hours.computeIfAbsent(local.truncatedTo(ChronoUnit.HOURS)) { ConcurrentHashMap() },
            days.computeIfAbsent(local.toLocalDate()) { ConcurrentHashMap() },
        )
        bucket = created
        prune(local)
        return created
    }

    private fun localTime(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone())

    /** The recorded days, newest first and then by event name; only [eventType] when given. */
    fun entries(eventType: String? = null): List<EventDay> =
        days.descendingMap().flatMap { (date, events) ->
            events.entries.sortedBy { it.key }.filter { eventType == null || it.key == eventType }
                .map { EventDay(date, it.key, it.value.calls.sum(), it.value.denied.sum()) }
        }

    /** The recorded hours, newest first and then by event name; only [eventType] when given. */
    fun hourly(eventType: String? = null): List<EventHour> =
        hours.descendingMap().flatMap { (hour, events) ->
            events.entries.sortedBy { it.key }.filter { eventType == null || it.key == eventType }
                .map { EventHour(hour, it.key, it.value.calls.sum(), it.value.denied.sum()) }
        }

    /** Writes days and hours to `event-history.csv` or `event-history.json` beside the history file; `null` if not possible. */
    fun export(json: Boolean): Path? {
        val source = file ?: return null
        if (retentionDays <= 0) return null
        val days = entries()
        val hours = hourly()
        val text = if (json) {
            fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            buildString {
                append("{\"days\":[")
                append(days.joinToString(",") { """{"date":"${it.date}","event":${quote(it.eventType)},"calls":${it.calls},"denied":${it.denied}}""" })
                append("],\"hours\":[")
                append(hours.joinToString(",") { """{"hour":"${it.hour}","event":${quote(it.eventType)},"calls":${it.calls},"denied":${it.denied}}""" })
                append("]}").append(NL)
            }
        } else {
            fun cell(value: String) = if (value.any { it == ',' || it == '"' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
            buildString {
                append("period,time,event,calls,denied").append(NL)
                days.forEach { append("day,${it.date},${cell(it.eventType)},${it.calls},${it.denied}").append(NL) }
                hours.forEach { append("hour,${it.hour},${cell(it.eventType)},${it.calls},${it.denied}").append(NL) }
            }
        }
        val target = source.resolveSibling(if (json) "event-history.json" else "event-history.csv")
        return runCatchingNonFatal { AtomicFiles.write(target, text.toByteArray(Charsets.UTF_8)); target }.getOrNull()
    }

    private fun prune(local: LocalDateTime) {
        val keepHours = minOf(retentionDays, MAX_HOURLY_DAYS) * 24L
        hours.headMap(local.truncatedTo(ChronoUnit.HOURS).minusHours(keepHours - 1)).clear()
        days.headMap(local.toLocalDate().minusDays(retentionDays.toLong() - 1)).clear()
    }

    private fun load(source: Path) {
        if (!Files.isRegularFile(source)) return
        Files.readAllLines(source).forEach { line ->
            val parts = line.split('\t')
            if (parts.size != 4) return@forEach
            val calls = parts[2].toLongOrNull() ?: return@forEach
            val denied = parts[3].toLongOrNull() ?: return@forEach
            val events = if ('T' in parts[0]) {
                val hour = runCatchingNonFatal { LocalDateTime.parse(parts[0]) }.getOrNull() ?: return@forEach
                hours.computeIfAbsent(hour) { ConcurrentHashMap() }
            } else {
                val date = runCatchingNonFatal { LocalDate.parse(parts[0]) }.getOrNull() ?: return@forEach
                days.computeIfAbsent(date) { ConcurrentHashMap() }
            }
            countsOf(events, parts[1]).add(calls, denied)
        }
        prune(localTime(now()))
    }

    /** Writes the file now when something changed; the write goes to a temporary file first. */
    fun flush() {
        val target = file ?: return
        if (retentionDays <= 0 || !dirty.getAndSet(false)) return
        prune(localTime(now()))
        val text = buildString {
            append(HEADER).append(NL)
            days.forEach { (date, events) -> appendLines(date, events) }
            hours.forEach { (hour, events) -> appendLines(hour, events) }
        }
        runCatchingNonFatal {
            Files.createDirectories(target.parent)
            AtomicFiles.write(target, text.toByteArray(Charsets.UTF_8))
        }.onFailure {
            dirty.set(true)
            report(it)
        }
    }

    private fun StringBuilder.appendLines(period: Any, events: Map<String, Counts>) {
        events.forEach { (name, counts) -> append("$period\t$name\t${counts.calls.sum()}\t${counts.denied.sum()}\n") }
    }

    /** A history that cannot be read or written is lost count by count, so the first failure of each kind is passed on. */
    private fun report(error: Throwable) {
        if (failureThrottle.shouldReport(error.javaClass.name)) onFailure(error)
    }

    override fun close() {
        worker?.close()
        flush()
    }

    private companion object {
        /** The first line of the history file, which names its format version. */
        const val HEADER = "# pnLibrary event history v1"
        const val MAX_HOURLY_DAYS = 30
        const val NL = '\n'
        const val MINUTE_MILLIS = 60_000L
    }
}
