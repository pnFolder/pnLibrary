package org.pnfolder.pnlibrary.api.activity

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
