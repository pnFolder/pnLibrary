package ru.privatenull.pnlibrary.core.observability

import ru.privatenull.pnlibrary.api.activity.ActivityCategory
import ru.privatenull.pnlibrary.api.activity.ActivityEvent
import ru.privatenull.pnlibrary.api.activity.ActivityQuery
import ru.privatenull.pnlibrary.api.activity.ActivitySeverity
import ru.privatenull.pnlibrary.api.observability.Observation
import ru.privatenull.pnlibrary.api.observability.ObservationLevel
import ru.privatenull.pnlibrary.api.observability.ObservationQuery
import ru.privatenull.pnlibrary.api.observability.ObservationRequest

/** Conversion rules kept in one place for the deprecated activity API. */
internal object LegacyActivityMapper {
    private const val CATEGORY_FIELD = "category"

    fun request(event: ActivityEvent): ObservationRequest = ObservationRequest(
        plugin = event.pluginId,
        source = event.source,
        message = event.type,
        level = ObservationLevel.valueOf(event.severity.name),
        data = event.metadata + mapOf(CATEGORY_FIELD to event.category.name),
    )

    fun query(query: ActivityQuery): ObservationQuery = ObservationQuery(
        limit = query.limit,
        plugin = query.pluginId,
        minimumLevel = query.minimumSeverity?.let { ObservationLevel.valueOf(it.name) },
        since = query.since,
        until = query.until,
    )

    fun event(observation: Observation): ActivityEvent = ActivityEvent(
        eventId = observation.id,
        timestamp = observation.timestamp,
        type = observation.message,
        category = category(observation.data[CATEGORY_FIELD]),
        severity = ActivitySeverity.valueOf(observation.level.name),
        source = observation.source,
        pluginId = observation.plugin,
        metadata = observation.data - CATEGORY_FIELD,
    )

    private fun category(value: String?): ActivityCategory {
        if (value == null) return ActivityCategory.SYSTEM
        return runCatching { ActivityCategory.valueOf(value) }.getOrDefault(ActivityCategory.SYSTEM)
    }
}
