package ru.privatenull.pnlibrary.api.activity

import java.util.UUID
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

/** Broad subsystem classification assigned to an activity event. */
enum class ActivityCategory {
    LIFECYCLE,
    USER_ACTION,
    UPDATE,
    DIAGNOSTICS,
    COMMAND,
    FEATURE,
    SECURITY,
    ERROR,
    SYSTEM
}

/**
 * Immutable record of one noteworthy library or plugin operation.
 *
 * @property eventId unique identifier used to associate attachments with this event
 * @property timestamp creation time expressed as Unix epoch milliseconds
 * @property type stable application-defined event type
 * @property category broad subsystem classification
 * @property severity importance used for filtering and retention
 * @property sessionId optional runtime-session identifier
 * @property correlationId optional identifier joining related operations
 * @property source component or class that produced the event
 * @property pluginId plugin responsible for the event, when applicable
 * @property metadata bounded structured details safe to include in diagnostics
 */
data class ActivityEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val category: ActivityCategory = ActivityCategory.SYSTEM,
    val severity: ActivitySeverity = ActivitySeverity.INFO,
    val sessionId: String? = null,
    val correlationId: String? = null,
    val source: String? = null,
    val pluginId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Filters applied when reading recent activity.
 *
 * @property limit maximum number of events to return
 * @property pluginId optional plugin identifier to match
 * @property category optional category to match
 * @property minimumSeverity least severe event to include
 * @property since inclusive lower timestamp bound in Unix epoch milliseconds
 * @property until inclusive upper timestamp bound in Unix epoch milliseconds
 */
data class ActivityQuery(
    val limit: Int = 50,
    val pluginId: String? = null,
    val category: ActivityCategory? = null,
    val minimumSeverity: ActivitySeverity? = null,
    val since: Long? = null,
    val until: Long? = null,
)

/**
 * Metadata describing a file or byte payload attached to an activity event.
 *
 * @property id unique attachment identifier
 * @property eventId identifier of the owning event
 * @property name caller-visible attachment name
 * @property contentType media type recorded for the payload
 * @property size payload size in bytes
 * @property sha256 lowercase SHA-256 digest used to verify payload integrity
 */
data class ActivityAttachment(
    val id: String,
    val eventId: String,
    val name: String,
    val contentType: String,
    val size: Long,
    val sha256: String,
)

/** Files selected for one activity record before they are persisted. */
typealias ActivityFile = Path

/**
 * One inspectable activity snapshot submitted as a single unit.
 *
 * @property files files to attach after the event is persisted
 * @property type stable application-defined event type
 * @property category broad subsystem classification
 * @property severity importance used for filtering and retention
 * @property sessionId optional runtime-session identifier
 * @property correlationId optional identifier joining related operations
 * @property source component or class that produced the snapshot
 * @property pluginId plugin responsible for the snapshot, when applicable
 * @property metadata bounded structured details safe to include in diagnostics
 */
data class ActivityBundle(
    val files: List<ActivityFile> = emptyList(),
    val type: String = "DIAGNOSTIC",
    val category: ActivityCategory = ActivityCategory.SYSTEM,
    val severity: ActivitySeverity = ActivitySeverity.INFO,
    val sessionId: String? = null,
    val correlationId: String? = null,
    val source: String? = null,
    val pluginId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

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
