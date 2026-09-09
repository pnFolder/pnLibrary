package ru.privatenull.pnlibrary.core.logging

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticRedactor
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

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
    @Volatile private var changeListener: ((List<Map<String, Any?>>) -> Unit)? = null

    fun onChange(listener: (List<Map<String, Any?>>) -> Unit) {
        changeListener = listener
    }

    /**
     * Records one occurrence. The first occurrence keeps the complete throwable; repeats only add their exact time.
     * The returned decision prevents identical stack traces from flooding the native console.
     */
    @Synchronized
    fun record(
        platform: PlatformAdapter,
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
                origin = error?.let(::origin),
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

    private fun notifyChanged() {
        changeListener?.let { listener -> runCatching { listener(snapshot()) } }
    }

    private fun fingerprint(plugin: String, level: LogLevel, message: String, error: Throwable?): String {
        val significant = unwrap(error)
        val frame = significant?.stackTrace?.firstOrNull(::isApplicationFrame)
            ?: significant?.stackTrace?.firstOrNull()
        val source = listOf(
            plugin,
            level.name,
            normalize(message),
            significant?.javaClass?.name.orEmpty(),
            normalize(significant?.message.orEmpty()),
            frame?.className.orEmpty(),
            frame?.methodName.orEmpty(),
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }

    private fun unwrap(error: Throwable?): Throwable? {
        var current = error ?: return null
        while ((current is CompletionException || current is ExecutionException) && current.cause != null) {
            current = current.cause!!
        }
        return current
    }

    private fun origin(error: Throwable): Map<String, Any?>? {
        val significant = unwrap(error) ?: error
        val frame = significant.stackTrace.firstOrNull(::isApplicationFrame)
            ?: significant.stackTrace.firstOrNull()
            ?: return null
        return linkedMapOf(
            "class" to frame.className,
            "method" to frame.methodName,
            "file" to frame.fileName,
            "line" to frame.lineNumber.takeIf { it >= 0 },
        )
    }

    private fun isApplicationFrame(frame: StackTraceElement): Boolean = IGNORED_FRAME_PREFIXES.none {
        frame.className.startsWith(it)
    }

    private fun fullStackTrace(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val redacted = redactor.redact(writer.toString())
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

    private fun normalize(value: String): String = value
        .replace(UUID_PATTERN, "<uuid>")
        .replace(LONG_NUMBER_PATTERN, "<number>")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()

    companion object {
        private const val MAX_MESSAGE_CHARS = 4_096
        private const val MAX_ERROR_CHARS = 1_048_576
        private const val MAX_TIMELINE_OCCURRENCES = 100_000
        private const val SUMMARY_INTERVAL_MS = 5 * 60 * 1_000L
        private val UUID_PATTERN = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b")
        private val LONG_NUMBER_PATTERN = Regex("\\b\\d{4,}\\b")
        private val WHITESPACE_PATTERN = Regex("\\s+")
        private val IGNORED_FRAME_PREFIXES = listOf(
            "java.", "javax.", "kotlin.", "kotlinx.", "sun.", "jdk.",
            "org.bukkit.", "net.minecraft.", "io.papermc.", "com.destroystokyo.paper.",
            "ru.privatenull.pnlibrary.",
        )
    }
}
