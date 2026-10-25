package org.pnfolder.pnlibrary.api.activity

import java.nio.file.Path

/** Relative importance used for filtering and retaining recorded activity. */
enum class ActivitySeverity {
    TRACE,
    INFO,
    NOTICE,
    WARNING,
    ERROR,
    CRITICAL
}

/** Files selected for one activity record before they are persisted. */
typealias ActivityFile = Path

/** Records bounded operational history and attachments for diagnostic reports. */
interface ActivityService : AutoCloseable {
    /** Persists [event] and returns the normalized stored representation. */
    fun record(event: ActivityEvent): ActivityEvent

    /**
     * Creates and records an event from individual fields.
     *
     * @return the normalized stored event
     */
    fun record(
        type: String,
        category: ActivityCategory = ActivityCategory.SYSTEM,
        severity: ActivitySeverity = ActivitySeverity.INFO,
        sessionId: String? = null,
        correlationId: String? = null,
        source: String? = null,
        pluginId: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): ActivityEvent = record(ActivityEvent(type = type, category = category, severity = severity,
        sessionId = sessionId, correlationId = correlationId, source = source,
        pluginId = pluginId, metadata = metadata))

    /** Returns recent events matching [query], ordered by the implementation's recency policy. */
    fun recent(query: ActivityQuery = ActivityQuery()): List<ActivityEvent>

    /** Records [bundle] and attaches each selected file to the resulting event. */
    fun record(bundle: ActivityBundle): ActivityEvent {
        val event = record(
            type = bundle.type,
            category = bundle.category,
            severity = bundle.severity,
            sessionId = bundle.sessionId,
            correlationId = bundle.correlationId,
            source = bundle.source,
            pluginId = bundle.pluginId,
            metadata = bundle.metadata,
        )
        bundle.files.forEach { file -> attachFile(event.eventId, file) }
        return event
    }

    /** Records a diagnostic snapshot without requiring callers to name its type. */
    fun diagnostic(
        files: List<Path> = emptyList(),
        metadata: Map<String, String> = emptyMap(),
        pluginId: String? = null,
        source: String? = null,
    ): ActivityEvent = record(ActivityBundle(files = files, metadata = metadata, pluginId = pluginId, source = source))

    /** Attaches in-memory [bytes] to an existing event and returns their stored metadata. */
    fun attach(eventId: String, name: String, contentType: String, bytes: ByteArray): ActivityAttachment

    /** Attaches the file at [path] to an existing event. */
    fun attachFile(eventId: String, path: Path, contentType: String = "application/octet-stream"): ActivityAttachment

    /** Records an event and attaches [path] to it through one caller-facing operation. */
    fun recordWithFile(
        path: Path,
        type: String = "DIAGNOSTIC",
        category: ActivityCategory = ActivityCategory.SYSTEM,
        severity: ActivitySeverity = ActivitySeverity.INFO,
        sessionId: String? = null,
        correlationId: String? = null,
        source: String? = null,
        pluginId: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): ActivityEvent {
        val event = record(type, category, severity, sessionId, correlationId, source, pluginId, metadata)
        attachFile(event.eventId, path)
        return event
    }
    /** Exports the persisted event journal in the implementation-defined report format. */
    fun exportJournal(): ByteArray

    /** Exports attachment payloads keyed by their stable archive paths. */
    fun exportAttachments(): Map<String, ByteArray>

    /** Exports metadata required to inspect and verify the attachment payloads. */
    fun exportAttachmentManifest(): ByteArray = ByteArray(0)

    /** Removes all retained events and attachments owned by this service. */
    fun clear()

    /** Flushes pending state and releases storage resources. */
    override fun close()
}
