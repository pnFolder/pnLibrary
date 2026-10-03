package ru.privatenull.pnlibrary.api.activity

import java.util.UUID
import java.nio.file.Path

enum class ActivitySeverity { TRACE, INFO, NOTICE, WARNING, ERROR, CRITICAL }

enum class ActivityCategory {
    LIFECYCLE, USER_ACTION, UPDATE, DIAGNOSTICS, COMMAND, FEATURE, SECURITY, ERROR, SYSTEM
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
    fun attach(eventId: String, name: String, contentType: String, bytes: ByteArray): ActivityAttachment
    fun attachFile(eventId: String, path: Path, contentType: String = "application/octet-stream"): ActivityAttachment
    fun exportJournal(): ByteArray
    fun clear()
    override fun close()
}
