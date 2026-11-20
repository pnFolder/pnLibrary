package org.pnfolder.pnlibrary.core.observability

import org.pnfolder.pnlibrary.api.activity.ActivityCategory
import org.pnfolder.pnlibrary.api.activity.ActivityEvent
import org.pnfolder.pnlibrary.api.activity.ActivityQuery
import org.pnfolder.pnlibrary.api.activity.ActivitySeverity
import org.pnfolder.pnlibrary.api.observability.Observation
import org.pnfolder.pnlibrary.api.observability.ObservationLevel
import org.pnfolder.pnlibrary.api.observability.ObservationQuery
import org.pnfolder.pnlibrary.api.observability.ObservationRequest
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal

// Conversion rules of the deprecated activity API, kept in one place.

private const val CATEGORY_FIELD = "category"

internal fun ActivityEvent.toObservationRequest(): ObservationRequest = ObservationRequest(
    plugin = pluginId,
    source = source,
    message = type,
    level = ObservationLevel.valueOf(severity.name),
    data = metadata + mapOf(CATEGORY_FIELD to category.name),
)

internal fun ActivityQuery.toObservationQuery(): ObservationQuery = ObservationQuery(
    limit = limit,
    plugin = pluginId,
    minimumLevel = minimumSeverity?.let { ObservationLevel.valueOf(it.name) },
    since = since,
    until = until,
)

internal fun Observation.toActivityEvent(): ActivityEvent = ActivityEvent(
    eventId = id,
    timestamp = timestamp,
    type = message,
    category = activityCategory(data[CATEGORY_FIELD]),
    severity = ActivitySeverity.valueOf(level.name),
    source = source,
    pluginId = plugin,
    metadata = data - CATEGORY_FIELD,
)

private fun activityCategory(value: String?): ActivityCategory {
    if (value == null) return ActivityCategory.SYSTEM
    return runCatchingNonFatal { ActivityCategory.valueOf(value) }.getOrDefault(ActivityCategory.SYSTEM)
}
