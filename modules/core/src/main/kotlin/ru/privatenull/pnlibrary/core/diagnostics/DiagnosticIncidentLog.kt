package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import java.time.Instant
import java.util.ArrayDeque

/** Bounded, deduplicated incident history for one plugin. */
internal class DiagnosticIncidentLog(
    private val capacity: Int,
    private val sanitizer: DiagnosticValueSanitizer,
) {
    private val incidents = ArrayDeque<LinkedHashMap<String, Any?>>()

    fun record(
        plugin: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        error: Throwable?,
        fields: Map<String, Any?>,
    ) = synchronized(incidents) {
        val now = Instant.now().toString()
        val incidentId = sanitizer.incidentId(plugin, level, component, code, message, error)
        val existing = incidents.firstOrNull { incident -> incident["incidentId"] == incidentId }

        if (existing == null) {
            incidents.addLast(newIncident(incidentId, now, level, component, code, message, fields, error))
            while (incidents.size > capacity) incidents.removeFirst()
        } else {
            appendOccurrence(existing, now, fields)
        }
    }

    fun snapshot(): List<Map<String, Any?>> = synchronized(incidents) {
        ArrayList(incidents)
    }

    fun summary(): Map<String, Any?> = synchronized(incidents) {
        val byLevel = linkedMapOf<String, Int>()
        val byComponent = linkedMapOf<String, Int>()
        var occurrences = 0L
        var omittedOccurrences = 0L
        incidents.forEach { incident ->
            val level = incident["level"]?.toString() ?: "UNKNOWN"
            val component = incident["component"]?.toString() ?: "unknown"
            byLevel[level] = (byLevel[level] ?: 0) + 1
            byComponent[component] = (byComponent[component] ?: 0) + 1
            occurrences += (incident["occurrenceCount"] as? Number)?.toLong() ?: 1L
            omittedOccurrences += (incident["omittedOccurrences"] as? Number)?.toLong() ?: 0L
        }
        linkedMapOf(
            "incidentCount" to incidents.size,
            "occurrenceCount" to occurrences,
            "omittedOccurrenceCount" to omittedOccurrences,
            "byLevel" to byLevel,
            "byComponent" to byComponent,
            "oldestUtc" to incidents.firstOrNull()?.get("firstSeenUtc"),
            "newestUtc" to incidents.lastOrNull()?.get("lastSeenUtc"),
        )
    }

    private fun appendOccurrence(
        incident: LinkedHashMap<String, Any?>,
        timeUtc: String,
        fields: Map<String, Any?>,
    ) {
        val previousCount = (incident["occurrenceCount"] as? Number)?.toLong() ?: 1L
        incident["occurrenceCount"] = previousCount + 1
        incident["lastSeenUtc"] = timeUtc

        @Suppress("UNCHECKED_CAST")
        val timeline = incident["occurrenceTimeline"] as MutableList<Map<String, Any?>>
        if (timeline.size < MAXIMUM_TIMELINE_ENTRIES) {
            timeline += occurrence(timeUtc, fields)
        } else {
            val omitted = (incident["omittedOccurrences"] as? Number)?.toLong() ?: 0L
            incident["omittedOccurrences"] = omitted + 1
        }
    }

    private fun newIncident(
        incidentId: String,
        timeUtc: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        fields: Map<String, Any?>,
        error: Throwable?,
    ): LinkedHashMap<String, Any?> = linkedMapOf<String, Any?>(
        "incidentId" to incidentId,
        "timeUtc" to timeUtc,
        "firstSeenUtc" to timeUtc,
        "lastSeenUtc" to timeUtc,
        "occurrenceCount" to 1L,
        "omittedOccurrences" to 0L,
        "level" to level.name,
        "component" to component,
        "code" to code,
        "message" to message,
        "fields" to fields,
        "origin" to error?.let(sanitizer::exceptionOrigin),
        "occurrenceTimeline" to mutableListOf(occurrence(timeUtc, fields)),
    ).also { incident ->
        if (error != null) incident["exception"] = sanitizer.exception(error)
    }

    private fun occurrence(timeUtc: String, fields: Map<String, Any?>): Map<String, Any?> = linkedMapOf(
        "timeUtc" to timeUtc,
        "thread" to Thread.currentThread().name,
        "fields" to fields,
    )

    private companion object {
        const val MAXIMUM_TIMELINE_ENTRIES = 100_000
    }
}
