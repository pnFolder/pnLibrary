package ru.privatenull.pnlibrary.core.diagnostics

import com.google.gson.GsonBuilder
import ru.privatenull.pnlibrary.api.activity.*
import ru.privatenull.pnlibrary.api.diagnostics.*
import ru.privatenull.pnlibrary.api.observability.*
import ru.privatenull.pnlibrary.core.observability.ObservabilityRuntime
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Compatibility facade. All event storage is owned by [ObservabilityRuntime]. */
internal class UnifiedObservabilityService(
    private val registry: DiagnosticsRegistry,
    private val runtime: ObservabilityRuntime,
) : ru.privatenull.pnlibrary.api.diagnostics.ObservabilityService {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    override val apiVersion: Int get() = registry.apiVersion
    override fun register(plugin: String, contributor: DiagnosticsContributor): DiagnosticRegistration =
        registry.register(plugin, contributor)
    override fun register(plugin: String, dataDirectory: Path, contributor: DiagnosticsContributor): DiagnosticRegistration =
        registry.register(plugin, dataDirectory, contributor)

    override fun status(plugin: String, component: String, state: String, detail: String, fields: Map<String, Any?>) {
        registry.status(plugin, component, state, detail, fields)
        runtime.status(ComponentStatus(plugin, component, state, detail, stringValues(fields)))
    }

    override fun clearStatus(plugin: String, component: String) = registry.clearStatus(plugin, component)
    override fun clearPlugin(plugin: String) = registry.clearPlugin(plugin)
    override fun record(plugin: String, level: DiagnosticLevel, component: String, code: String, message: String, error: Throwable?, fields: Map<String, Any?>) =
        registry.record(plugin, level, component, code, message, error, fields)

    override fun record(request: ObservationRequest): Observation = runtime.record(request)
    override fun recent(query: ObservationQuery): List<Observation> = runtime.recent(query)
    override fun status(status: ComponentStatus): ComponentStatus = runtime.status(status)
    override fun createReport(request: ObservabilityReportRequest): ObservabilityReport = runtime.createReport(request)

    override fun record(event: ActivityEvent): ActivityEvent {
        val observation = runtime.record(ObservationRequest(
            plugin = event.pluginId,
            source = event.source,
            message = event.type,
            level = event.severity.toObservationLevel(),
            data = event.metadata + mapOf("category" to event.category.name),
        ))
        return event.copy(eventId = observation.id, timestamp = observation.timestamp)
    }

    override fun recent(query: ActivityQuery): List<ActivityEvent> = runtime.recent(ObservationQuery(
        limit = query.limit,
        plugin = query.pluginId,
        minimumLevel = query.minimumSeverity?.toObservationLevel(),
        since = query.since,
        until = query.until,
    )).map(Observation::toActivityEvent)

    override fun attach(eventId: String, name: String, contentType: String, bytes: ByteArray): ActivityAttachment {
        val temporary = Files.createTempFile("pnlibrary-attachment-", "-${safeName(name)}")
        return try {
            Files.write(temporary, bytes)
            attachFile(eventId, temporary, contentType).copy(name = name, contentType = contentType)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override fun attachFile(eventId: String, path: Path, contentType: String): ActivityAttachment {
        val stored = runtime.attachFile(eventId, path)
        return ActivityAttachment(stored.id, eventId, stored.originalName, stored.contentType, stored.size, stored.sha256)
    }

    override fun exportJournal(): ByteArray = runtime.recent(ObservationQuery(limit = Int.MAX_VALUE))
        .joinToString(separator = "\n", postfix = "\n") { gson.toJson(it) }
        .toByteArray(StandardCharsets.UTF_8)

    override fun exportAttachments(): Map<String, ByteArray> = runtime.allAttachments()
        .associate { it.id to runtime.attachmentBytes(it) }

    override fun exportAttachmentManifest(): ByteArray = gson.toJson(runtime.allAttachments().map {
        mapOf("id" to it.id, "eventId" to it.observationId, "name" to it.originalName,
            "contentType" to it.contentType, "size" to it.size, "sha256" to it.sha256)
    }).toByteArray(StandardCharsets.UTF_8)

    override fun clear() = runtime.clear()
    override fun close() = runtime.close()

    private fun stringValues(values: Map<String, Any?>) =
        values.mapValues { (_, value) -> value?.toString() ?: "null" }
    private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun ActivitySeverity.toObservationLevel(): ObservationLevel = ObservationLevel.valueOf(name)

private fun Observation.toActivityEvent(): ActivityEvent = ActivityEvent(
    eventId = id,
    timestamp = timestamp,
    type = message,
    category = data["category"]?.let { runCatching { ActivityCategory.valueOf(it) }.getOrNull() }
        ?: ActivityCategory.SYSTEM,
    severity = ActivitySeverity.valueOf(level.name),
    source = source,
    pluginId = plugin,
    metadata = data - "category",
)
