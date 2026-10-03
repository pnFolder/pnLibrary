package ru.privatenull.pnlibrary.core.observability

import ru.privatenull.pnlibrary.api.activity.ActivityAttachment
import ru.privatenull.pnlibrary.api.activity.ActivityEvent
import ru.privatenull.pnlibrary.api.activity.ActivityQuery
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsContributor
import ru.privatenull.pnlibrary.api.diagnostics.ObservabilityService
import ru.privatenull.pnlibrary.api.observability.ComponentStatus
import ru.privatenull.pnlibrary.api.observability.Observation
import ru.privatenull.pnlibrary.api.observability.ObservationQuery
import ru.privatenull.pnlibrary.api.observability.ObservationRequest
import ru.privatenull.pnlibrary.api.observability.ObservabilityReport
import ru.privatenull.pnlibrary.api.observability.ObservabilityReportRequest
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import java.nio.file.Path

/**
 * Keeps old diagnostics and activity calls source-compatible.
 *
 * This class performs translation only. [ObservabilityRuntime] owns all event
 * storage, attachment storage, filtering, retention, and report snapshots.
 */
internal class LegacyObservabilityAdapter(
    private val diagnostics: DiagnosticsRegistry,
    private val runtime: ObservabilityRuntime,
) : ObservabilityService {
    override val apiVersion: Int
        get() = diagnostics.apiVersion

    override fun register(
        plugin: String,
        contributor: DiagnosticsContributor,
    ): DiagnosticRegistration = diagnostics.register(plugin, contributor)

    override fun register(
        plugin: String,
        dataDirectory: Path,
        contributor: DiagnosticsContributor,
    ): DiagnosticRegistration = diagnostics.register(plugin, dataDirectory, contributor)

    override fun status(
        plugin: String,
        component: String,
        state: String,
        detail: String,
        fields: Map<String, Any?>,
    ) {
        diagnostics.status(plugin, component, state, detail, fields)
        runtime.status(
            ComponentStatus(
                plugin = plugin,
                component = component,
                state = state,
                detail = detail,
                data = fields.toStringValues(),
            ),
        )
    }

    override fun clearStatus(plugin: String, component: String) {
        diagnostics.clearStatus(plugin, component)
    }

    override fun clearPlugin(plugin: String) {
        diagnostics.clearPlugin(plugin)
    }

    override fun record(
        plugin: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        error: Throwable?,
        fields: Map<String, Any?>,
    ) {
        diagnostics.record(plugin, level, component, code, message, error, fields)
    }

    override fun record(request: ObservationRequest): Observation = runtime.record(request)

    override fun recent(query: ObservationQuery): List<Observation> = runtime.recent(query)

    override fun status(status: ComponentStatus): ComponentStatus = runtime.status(status)

    override fun createReport(request: ObservabilityReportRequest): ObservabilityReport =
        runtime.createReport(request)

    override fun record(event: ActivityEvent): ActivityEvent {
        val observation = runtime.record(LegacyActivityMapper.request(event))
        return event.copy(eventId = observation.id, timestamp = observation.timestamp)
    }

    override fun recent(query: ActivityQuery): List<ActivityEvent> =
        runtime.recent(LegacyActivityMapper.query(query)).map(LegacyActivityMapper::event)

    override fun attach(
        eventId: String,
        name: String,
        contentType: String,
        bytes: ByteArray,
    ): ActivityAttachment = runtime.attachBytes(
        observationId = eventId,
        name = name,
        contentType = contentType,
        bytes = bytes,
    ).toLegacyAttachment(eventId)

    override fun attachFile(
        eventId: String,
        path: Path,
        contentType: String,
    ): ActivityAttachment = runtime.attachFile(eventId, path).toLegacyAttachment(eventId)

    override fun exportJournal(): ByteArray = runtime.reportSnapshot().journal

    override fun exportAttachments(): Map<String, ByteArray> = runtime.reportSnapshot().attachments

    override fun exportAttachmentManifest(): ByteArray = runtime.reportSnapshot().attachmentManifest

    override fun clear() {
        runtime.clear()
    }

    override fun close() {
        runtime.close()
    }

    private fun StoredAttachment.toLegacyAttachment(eventId: String): ActivityAttachment =
        ActivityAttachment(
            id = id,
            eventId = eventId,
            name = originalName,
            contentType = contentType,
            size = size,
            sha256 = sha256,
        )
}
