package org.pnfolder.pnlibrary.api.activity

import java.util.UUID

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
