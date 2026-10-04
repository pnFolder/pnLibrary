package ru.privatenull.pnlibrary.api.activity

import java.util.UUID
import java.nio.file.Path

enum class ActivitySeverity {
    TRACE,
    INFO,
    NOTICE,
    WARNING,
    ERROR,
    CRITICAL
}

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

data class ActivityQuery(
    val limit: Int = 50,
    val pluginId: String? = null,
    val category: ActivityCategory? = null,
    val minimumSeverity: ActivitySeverity? = null,
    val since: Long? = null,
    val until: Long? = null,
)

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

/** One inspectable activity snapshot submitted as a single unit. */
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

interface ActivityService : AutoCloseable {
    fun record(event: ActivityEvent): ActivityEvent

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

    fun recent(query: ActivityQuery = ActivityQuery()): List<ActivityEvent>

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

    fun attach(eventId: String, name: String, contentType: String, bytes: ByteArray): ActivityAttachment

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
    fun exportJournal(): ByteArray

    fun exportAttachments(): Map<String, ByteArray>

    fun exportAttachmentManifest(): ByteArray = ByteArray(0)

    fun clear()

    override fun close()
}
