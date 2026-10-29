package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.api.events.EventHour
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-day call counters of every event type, kept for [retentionDays] and stored in a small text file so the
 * history survives restarts.
 *
 * Counting is a map update under a lock. Writing happens on a background worker at most once per [flushEveryMillis]
 * and once more on [close], so a crash loses at most a minute of counts. A retention of `0` keeps nothing.
 * The file starts with a `# pnLibrary event history v1` line and then holds one `date<TAB>event<TAB>calls<TAB>denied`
 * line per day and event; lines that do not fit that shape are ignored. Hourly counters of the last 30 days at most
 * are stored the same way, with `yyyy-MM-ddTHH:00` in the first column.
 */
internal class EventHistory(
    private val file: Path?,
    private val retentionDays: Int,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val flushEveryMillis: Long = 60_000,
) : AutoCloseable {
    private class Counts(var calls: Long = 0, var denied: Long = 0)

    private val days = TreeMap<LocalDate, MutableMap<String, Counts>>()
    private val hours = TreeMap<LocalDateTime, MutableMap<String, Counts>>()
    private val dirty = AtomicBoolean(false)
    private val lastFlush = AtomicLong(clock())
    private val worker = if (file != null && retentionDays > 0) ManagedWorker("pnLibrary-event-history") else null

    /** How many days are kept; `0` means the history is off. */
    val retention: Int get() = retentionDays

    init {
        if (file != null && retentionDays > 0) runCatchingNonFatal { load(file) }
    }

    /** Counts one call of [eventType]. */
    fun record(eventType: String, denied: Boolean) {
        if (retentionDays <= 0) return
        val now = LocalDateTime.now(zone())
        val today = now.toLocalDate()
        synchronized(days) {
            val counts = days.getOrPut(today) { HashMap() }.getOrPut(eventType) { Counts() }
            counts.calls++
            if (denied) counts.denied++
            val hourly = hours.getOrPut(now.truncatedTo(ChronoUnit.HOURS)) { HashMap() }.getOrPut(eventType) { Counts() }
            hourly.calls++
            if (denied) hourly.denied++
            pruneHours(now)
            if (days.firstKey().isBefore(today.minusDays(retentionDays.toLong() - 1))) prune(today)
        }
        dirty.set(true)
        val tick = clock()
        val previous = lastFlush.get()
        if (tick - previous >= flushEveryMillis && lastFlush.compareAndSet(previous, tick)) {
            worker?.let { runCatchingNonFatal { it.submit { flush() } } }
        }
    }

    /** The recorded days, newest first and then by event name; only [eventType] when given. */
    fun entries(eventType: String? = null): List<EventDay> = synchronized(days) {
        days.descendingMap().flatMap { (date, events) ->
            events.entries.sortedBy { it.key }.filter { eventType == null || it.key == eventType }
                .map { EventDay(date, it.key, it.value.calls, it.value.denied) }
        }
    }

    /** The recorded hours, newest first and then by event name; only [eventType] when given. */
    fun hourly(eventType: String? = null): List<EventHour> = synchronized(days) {
        hours.descendingMap().flatMap { (hour, events) ->
            events.entries.sortedBy { it.key }.filter { eventType == null || it.key == eventType }
                .map { EventHour(hour, it.key, it.value.calls, it.value.denied) }
        }
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

    private fun pruneHours(now: LocalDateTime) {
        val keep = minOf(retentionDays, MAX_HOURLY_DAYS) * 24L
        hours.headMap(now.truncatedTo(ChronoUnit.HOURS).minusHours(keep - 1)).clear()
    }

    private fun prune(today: LocalDate) {
        val oldest = today.minusDays(retentionDays.toLong() - 1)
        days.headMap(oldest).clear()
    }

    private fun load(source: Path) {
        if (!Files.isRegularFile(source)) return
        val today = LocalDate.now(zone())
        synchronized(days) {
            Files.readAllLines(source).forEach { line ->
                val parts = line.split('\t')
                if (parts.size != 4) return@forEach
                if ('T' in parts[0]) {
                    val hour = runCatchingNonFatal { LocalDateTime.parse(parts[0]) }.getOrNull() ?: return@forEach
                    val calls = parts[2].toLongOrNull() ?: return@forEach
                    val denied = parts[3].toLongOrNull() ?: return@forEach
                    val counts = hours.getOrPut(hour) { HashMap() }.getOrPut(parts[1]) { Counts() }
                    counts.calls += calls
                    counts.denied += denied
                    return@forEach
                }
                val date = runCatchingNonFatal { LocalDate.parse(parts[0]) }.getOrNull() ?: return@forEach
                val calls = parts[2].toLongOrNull() ?: return@forEach
                val denied = parts[3].toLongOrNull() ?: return@forEach
                val counts = days.getOrPut(date) { HashMap() }.getOrPut(parts[1]) { Counts() }
                counts.calls += calls
                counts.denied += denied
            }
            prune(today)
            pruneHours(LocalDateTime.now(zone()))
        }
    }

    /** Writes the file now when something changed; the write goes to a temporary file first. */
    fun flush() {
        val target = file ?: return
        if (retentionDays <= 0 || !dirty.getAndSet(false)) return
        val text = synchronized(days) {
            buildString {
                append(HEADER).append('\n')
                days.forEach { (date, events) ->
                    events.forEach { (name, counts) ->
                        append("$date\t$name\t${counts.calls}\t${counts.denied}\n")
                    }
                }
                hours.forEach { (hour, events) ->
                    events.forEach { (name, counts) ->
                        append("$hour\t$name\t${counts.calls}\t${counts.denied}\n")
                    }
                }
            }
        }
        runCatchingNonFatal {
            Files.createDirectories(target.parent)
            AtomicFiles.write(target, text.toByteArray(Charsets.UTF_8))
        }.onFailure { dirty.set(true) }
    }

    override fun close() {
        worker?.close()
        flush()
    }

    private companion object {
        const val HEADER = "# pnLibrary event history v1"
        const val MAX_HOURLY_DAYS = 30
        const val NL = '\n'
    }
}
