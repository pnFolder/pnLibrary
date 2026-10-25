package org.pnfolder.pnlibrary.api.activity

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
