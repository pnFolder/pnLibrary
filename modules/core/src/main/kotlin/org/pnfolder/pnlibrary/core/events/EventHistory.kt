package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-day call counters of every event type, kept for [retentionDays] and stored in a small text file so the
 * history survives restarts.
 *
 * Counting is a map update under a lock. Writing happens on a background worker at most once per [flushEveryMillis]
 * and once more on [close], so a crash loses at most a minute of counts. A retention of `0` keeps nothing.
 * The file holds one `date<TAB>event<TAB>calls<TAB>denied` line per day and event.
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
    private val dirty = AtomicBoolean(false)
    private var lastFlush = clock()
    private val worker = if (file != null && retentionDays > 0) ManagedWorker("pnLibrary-event-history") else null

    /** How many days are kept; `0` means the history is off. */
    val retention: Int get() = retentionDays

    init {
        if (file != null && retentionDays > 0) runCatchingNonFatal { load(file) }
    }

    /** Counts one call of [eventType]. */
    fun record(eventType: String, denied: Boolean) {
        if (retentionDays <= 0) return
        val today = LocalDate.now(zone())
        synchronized(days) {
            val counts = days.getOrPut(today) { HashMap() }.getOrPut(eventType) { Counts() }
            counts.calls++
            if (denied) counts.denied++
            if (days.firstKey().isBefore(today.minusDays(retentionDays.toLong() - 1))) prune(today)
        }
        dirty.set(true)
        val now = clock()
        if (now - lastFlush >= flushEveryMillis) {
            lastFlush = now
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
                val date = runCatchingNonFatal { LocalDate.parse(parts[0]) }.getOrNull() ?: return@forEach
                val calls = parts[2].toLongOrNull() ?: return@forEach
                val denied = parts[3].toLongOrNull() ?: return@forEach
                val counts = days.getOrPut(date) { HashMap() }.getOrPut(parts[1]) { Counts() }
                counts.calls += calls
                counts.denied += denied
            }
            prune(today)
        }
    }

    /** Writes the file now when something changed; the write goes to a temporary file first. */
    fun flush() {
        val target = file ?: return
        if (retentionDays <= 0 || !dirty.getAndSet(false)) return
        val text = synchronized(days) {
            buildString {
                days.forEach { (date, events) ->
                    events.forEach { (name, counts) ->
                        append("$date\t$name\t${counts.calls}\t${counts.denied}\n")
                    }
                }
            }
        }
        runCatchingNonFatal {
            Files.createDirectories(target.parent)
            val temporary = target.resolveSibling(target.fileName.toString() + ".tmp")
            Files.write(temporary, text.toByteArray(Charsets.UTF_8))
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { dirty.set(true) }
    }

    override fun close() {
        worker?.close()
        flush()
    }
}
