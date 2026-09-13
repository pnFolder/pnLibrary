package ru.privatenull.pnlibrary.api.activity

import java.util.UUID

/** Severity for an activity event. */
enum class ActivitySeverity { TRACE, INFO, NOTICE, WARNING, ERROR, CRITICAL }

/** High-level activity category used by analytics UIs and filters. */
enum class ActivityCategory {
    LIFECYCLE,
    USER_ACTION,
    UPDATE,
    DIAGNOSTICS,
    COMMAND,
    FEATURE,
    SECURITY,
    ERROR,
    SYSTEM,
}

/**
 * One immutable activity/audit event.
 *
 * Metadata is intentionally string-only so transports and dashboards can consume it safely.
 * Callers should only place non-sensitive, allow-listed values here.
 */
data class ActivityEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val category: ActivityCategory = ActivityCategory.USER_ACTION,
    val severity: ActivitySeverity = ActivitySeverity.INFO,
    val actorId: String? = null,
    val sessionId: String? = null,
    val correlationId: String? = null,
    val source: String? = null,
    val action: String? = null,
    val target: String? = null,
    val pluginId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/** Filters for newest-first activity queries. */
data class ActivityQuery(
    val limit: Int = 50,
    val actorId: String? = null,
    val sessionId: String? = null,
    val correlationId: String? = null,
    val pluginId: String? = null,
    val category: ActivityCategory? = null,
    val minimumSeverity: ActivitySeverity? = null,
    val types: Set<String> = emptySet(),
    val since: Long? = null,
    val until: Long? = null,
)

/**
 * Process-wide activity intelligence service.
 *
 * This is intentionally separate from bStats: bStats remains aggregate telemetry,
 * while this service captures detailed product/audit events for first-party tooling.
 */
interface ActivityService : AutoCloseable {
    /** Stores [event] and returns the normalized event that was accepted. */
    fun record(event: ActivityEvent): ActivityEvent

    /** Convenience API for simple activity recording. */
    fun record(
        type: String,
        category: ActivityCategory = ActivityCategory.USER_ACTION,
        severity: ActivitySeverity = ActivitySeverity.INFO,
        actorId: String? = null,
        sessionId: String? = null,
        correlationId: String? = null,
        source: String? = null,
        action: String? = null,
        target: String? = null,
        pluginId: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): ActivityEvent = record(
        ActivityEvent(
            type = type,
            category = category,
            severity = severity,
            actorId = actorId,
            sessionId = sessionId,
            correlationId = correlationId,
            source = source,
            action = action,
            target = target,
            pluginId = pluginId,
            metadata = metadata,
        )
    )

    /** Returns newest events first. */
    fun recent(query: ActivityQuery = ActivityQuery()): List<ActivityEvent>

    /** Removes all locally retained activity records. */
    fun clear()

    override fun close()
}
