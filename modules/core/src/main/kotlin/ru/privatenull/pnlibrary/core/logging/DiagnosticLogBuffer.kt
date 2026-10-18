package ru.privatenull.pnlibrary.core.logging

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticRedactor
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticThrowables
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.ArrayDeque
import java.util.LinkedHashMap

/** Bounded session history that aggregates repeated warnings and errors without losing their timeline. */
internal class DiagnosticLogBuffer(private val capacity: Int = 2_000) {
    internal data class CaptureResult(val emitOriginal: Boolean, val summary: String? = null)

    private data class Occurrence(val timeUtc: String, val thread: String)

    private data class Incident(
        val id: String,
        val level: LogLevel,
        val plugin: String,
        val message: String,
        val exceptionType: String?,
        val origin: Map<String, Any?>?,
        val fullError: String?,
        val consoleBlock: String,
        val firstSeenUtc: String,
        var lastSeenUtc: String,
        var lastSummaryAtMs: Long,
        var count: Long = 1,
        var omittedOccurrences: Long = 0,
        val occurrences: ArrayDeque<Occurrence> = ArrayDeque(),
    )

    private val incidents = LinkedHashMap<String, Incident>()
    private val redactor = DiagnosticRedactor()
    private var timelineEntryCount = 0
    @Volatile
    private var changeListener: ((List<Map<String, Any?>>) -> Unit)? = null

    fun onChange(listener: (List<Map<String, Any?>>) -> Unit) {
        changeListener = listener
    }

    /**
     * Records one occurrence. The first occurrence keeps the complete throwable; repeats only add their exact time.
     * The returned decision prevents identical stack traces from flooding the native console.
     */
    @Synchronized
    fun record(
        platform: PlatformOwnership,
        owner: Any,
        level: LogLevel,
        message: String,
        error: Throwable?,
    ): CaptureResult? {
        if (level != LogLevel.WARNING && level != LogLevel.ERROR) return null

        val nowMs = System.currentTimeMillis()
        val now = Instant.ofEpochMilli(nowMs).toString()
        val plugin = redactor.redact(platform.ownerDetails(owner)["name"] ?: owner.javaClass.simpleName).take(96)
        val safeMessage = redactor.redact(message).take(MAX_MESSAGE_CHARS)
        val fingerprint = fingerprint(plugin, level, safeMessage, error)
        val existing = incidents[fingerprint]

        if (existing == null) {
            val completeError = error?.let(::fullStackTrace)
            val incident = Incident(
                id = fingerprint,
                level = level,
                plugin = plugin,
                message = safeMessage,
                exceptionType = error?.javaClass?.name,
                origin = error?.let(DiagnosticThrowables::origin),
                fullError = completeError,
                consoleBlock = consoleBlock(now, level, safeMessage, completeError),
                firstSeenUtc = now,
                lastSeenUtc = now,
                lastSummaryAtMs = nowMs,
            )
            addOccurrence(incident, now)
            incidents[fingerprint] = incident
            while (incidents.size > capacity.coerceAtLeast(1)) {
                incidents.remove(incidents.keys.first())?.let {
                    timelineEntryCount -= it.occurrences.size
                }
            }
            notifyChanged()
            return CaptureResult(emitOriginal = true)
        }

        existing.count++
        existing.lastSeenUtc = now
        addOccurrence(existing, now)
        notifyChanged()

        val milestone = existing.count == 10L || existing.count == 100L || existing.count % 1_000L == 0L
        val intervalElapsed = nowMs - existing.lastSummaryAtMs >= SUMMARY_INTERVAL_MS
        if (!milestone && !intervalElapsed) return CaptureResult(emitOriginal = false)

        existing.lastSummaryAtMs = nowMs
        return CaptureResult(
            emitOriginal = false,
            summary = "[$plugin] Repeated incident ${existing.id}: ${existing.message} " +
                "(${existing.count} occurrences, ${existing.firstSeenUtc} — ${existing.lastSeenUtc})",
        )
    }

    /** Removes incidents last seen before [cutoffMs]; returns how many were dropped. */
    @Synchronized
    fun expireOlderThan(cutoffMs: Long): Int {
        val expired = incidents.values.filter { Instant.parse(it.lastSeenUtc).toEpochMilli() < cutoffMs }
        expired.forEach { incident ->
            incidents.remove(incident.id)
            timelineEntryCount -= incident.occurrences.size
        }
        if (expired.isNotEmpty()) notifyChanged()
        return expired.size
    }

    @Synchronized
    fun snapshot(): List<Map<String, Any?>> = incidents.values.map { incident ->
        linkedMapOf<String, Any?>(
            "incidentId" to incident.id,
            "level" to incident.level.name,
            "plugin" to incident.plugin,
            "message" to incident.message,
            "exceptionType" to incident.exceptionType,
            "origin" to incident.origin,
            "occurrences" to linkedMapOf(
                "count" to incident.count,
                "firstSeenUtc" to incident.firstSeenUtc,
                "lastSeenUtc" to incident.lastSeenUtc,
                "timeline" to incident.occurrences.map {
                    linkedMapOf("timeUtc" to it.timeUtc, "thread" to it.thread)
                },
                "omitted" to incident.omittedOccurrences,
            ),
            "fullError" to incident.fullError,
            "consoleBlock" to incident.consoleBlock,
        )
    }

    @Synchronized
    fun summary(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "incidentCount" to incidents.size,
        "occurrenceCount" to incidents.values.sumOf { it.count },
        "omittedOccurrenceCount" to incidents.values.sumOf { it.omittedOccurrences },
        "byLevel" to incidents.values.groupingBy { it.level.name }.eachCount(),
        "byPlugin" to incidents.values.groupingBy { it.plugin }.eachCount(),
        "exceptionTypes" to incidents.values.mapNotNull { it.exceptionType }.groupingBy { it }.eachCount(),
        "oldestUtc" to incidents.values.firstOrNull()?.firstSeenUtc,
        "newestUtc" to incidents.values.maxByOrNull { it.lastSeenUtc }?.lastSeenUtc,
    )

    private fun notifyChanged() {
        changeListener?.let { listener -> runCatching { listener(snapshot()) } }
    }

    private fun fingerprint(plugin: String, level: LogLevel, message: String, error: Throwable?): String =
        DiagnosticThrowables.fingerprint(listOf(plugin, level.name), message, error)

    private fun fullStackTrace(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val redacted = redactor.redactStackTrace(writer.toString())
        if (redacted.length <= MAX_ERROR_CHARS) return redacted
        return redacted.take(MAX_ERROR_CHARS) +
            "\n[TRUNCATED: throwable exceeded the hard ${MAX_ERROR_CHARS}-character safety limit]"
    }

    private fun addOccurrence(incident: Incident, timeUtc: String) {
        if (timelineEntryCount < MAX_TIMELINE_OCCURRENCES) {
            incident.occurrences.addLast(Occurrence(timeUtc, Thread.currentThread().name))
            timelineEntryCount++
        } else {
            incident.omittedOccurrences++
        }
    }

    private fun consoleBlock(timeUtc: String, level: LogLevel, message: String, fullError: String?): String =
        buildString {
            append('[').append(timeUtc).append(' ').append(level.name).append("] ").append(message)
            fullError?.let { append('\n').append(it) }
        }

    companion object {
        private const val MAX_MESSAGE_CHARS = 4_096
        private const val MAX_ERROR_CHARS = 1_048_576
        private const val MAX_TIMELINE_OCCURRENCES = 100_000
        private const val SUMMARY_INTERVAL_MS = 5 * 60 * 1_000L
    }
}
