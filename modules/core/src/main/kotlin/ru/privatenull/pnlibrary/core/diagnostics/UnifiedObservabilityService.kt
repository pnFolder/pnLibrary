package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.activity.*
import ru.privatenull.pnlibrary.api.diagnostics.*
import java.nio.file.Path

/** Facade joining the legacy diagnostics registry and the activity journal. */
internal class UnifiedObservabilityService(
    private val registry: DiagnosticsRegistry,
    private val journal: ActivityService,
) : ObservabilityService {
    override val apiVersion: Int get() = registry.apiVersion
    override fun register(plugin: String, contributor: DiagnosticsContributor): DiagnosticRegistration =
        registry.register(plugin, contributor)

    override fun register(plugin: String, dataDirectory: Path, contributor: DiagnosticsContributor): DiagnosticRegistration =
        registry.register(plugin, dataDirectory, contributor)

    override fun status(plugin: String, component: String, state: String, detail: String, fields: Map<String, Any?>) =
        registry.status(plugin, component, state, detail, fields)
    override fun clearStatus(plugin: String, component: String) = registry.clearStatus(plugin, component)
    override fun clearPlugin(plugin: String) = registry.clearPlugin(plugin)
    override fun record(
        plugin: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        error: Throwable?,
        fields: Map<String, Any?>,
    ) = registry.record(plugin, level, component, code, message, error, fields)

    override fun record(event: ActivityEvent): ActivityEvent = journal.record(event)
    override fun recent(query: ActivityQuery): List<ActivityEvent> = journal.recent(query)
    override fun attach(eventId: String, name: String, contentType: String, bytes: ByteArray): ActivityAttachment = journal.attach(eventId, name, contentType, bytes)
    override fun attachFile(eventId: String, path: Path, contentType: String): ActivityAttachment = journal.attachFile(eventId, path, contentType)
    override fun exportJournal(): ByteArray = journal.exportJournal()
    override fun exportAttachments(): Map<String, ByteArray> = journal.exportAttachments()
    override fun exportAttachmentManifest(): ByteArray = journal.exportAttachmentManifest()
    override fun clear() = journal.clear()
    override fun close() = journal.close()
}
