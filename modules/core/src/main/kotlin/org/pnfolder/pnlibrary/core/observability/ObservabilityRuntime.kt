package org.pnfolder.pnlibrary.core.observability

import org.pnfolder.pnlibrary.api.observability.ComponentStatus
import org.pnfolder.pnlibrary.api.observability.ObservabilityReport
import org.pnfolder.pnlibrary.api.observability.ObservabilityReportRequest
import org.pnfolder.pnlibrary.api.observability.ObservabilityService
import org.pnfolder.pnlibrary.api.observability.Observation
import org.pnfolder.pnlibrary.api.observability.ObservationKeys
import org.pnfolder.pnlibrary.api.observability.ObservationLevel
import org.pnfolder.pnlibrary.api.observability.ObservationQuery
import org.pnfolder.pnlibrary.api.observability.ObservationRequest
import java.nio.file.Path
import java.time.Instant

/** Records observations with their attachments and component statuses, and applies retention to them. */
internal class ObservabilityRuntime(
    dataFolder: Path,
    private val clock: () -> Long = System::currentTimeMillis,
    reportFactory: ((ObservabilityReportRequest) -> ObservabilityReport)? = null,
) : ObservabilityService {
    private val recordLock = Any()
    private val journal = ObservationJournal(dataFolder, clock)
    private val attachmentStore = AttachmentStore(dataFolder)
    private val statusRegistry = ComponentStatusRegistry()
    @Volatile
    private var reportFactory = reportFactory

    init {
        maintain()
    }

    /** Drops expired observations and the attachments that no longer belong to a retained one. */
    fun maintain() = synchronized(recordLock) {
        val activeObservationIds = journal.applyRetention(clock())
        attachmentStore.removeOrphans(activeObservationIds)
    }

    override fun record(request: ObservationRequest): Observation = synchronized(recordLock) {
        val timestamp = clock()
        val keepFor = request.data[ObservationKeys.KEEP_FOR_MS]?.toLongOrNull()
        val stored = Observation.from(request.copy(data = request.data - ObservationKeys.KEEP_FOR_MS), timestamp)
        val observation = if (keepFor == null) {
            stored
        } else {
            stored.copy(data = stored.data + (ObservationKeys.KEEP_UNTIL_MS to (timestamp + keepFor).toString()))
        }
        request.files.forEach { file -> attachmentStore.save(observation.id, file) }
        journal.append(observation)
        observation
    }

    override fun recent(query: ObservationQuery): List<Observation> {
        return journal.recent()
            .filter { observation -> query.matches(observation) }
            .takeLast(query.limit.coerceAtLeast(0))
    }

    override fun status(status: ComponentStatus): ComponentStatus = statusRegistry.update(status)

    override fun createReport(request: ObservabilityReportRequest): ObservabilityReport {
        val factory = reportFactory ?: error("Observability report builder is not configured")
        return factory(request)
    }

    internal fun configureReportFactory(factory: (ObservabilityReportRequest) -> ObservabilityReport) {
        reportFactory = factory
    }

    internal fun statuses(): List<ComponentStatus> = statusRegistry.snapshot()

    internal fun attachments(observationId: String): List<StoredAttachment> =
        attachmentStore.forObservation(observationId)

    internal fun attachFile(observationId: String, path: Path): StoredAttachment = synchronized(recordLock) {
        attachmentStore.save(observationId, path)
    }

    internal fun attachBytes(
        observationId: String,
        name: String,
        contentType: String,
        bytes: ByteArray,
    ): StoredAttachment = synchronized(recordLock) {
        attachmentStore.save(observationId, name, contentType, bytes)
    }

    internal fun allAttachments(): List<StoredAttachment> = attachmentStore.all()

    internal fun analytics(): Map<String, Any?> = synchronized(recordLock) {
        val events = journal.recent()
        val attachments = attachmentStore.all()
        val statuses = statusRegistry.snapshot()
        linkedMapOf(
            "status" to health(events, statuses),
            "eventCount" to events.size,
            "byLevel" to events.groupingBy { it.level.name }.eachCount(),
            "bySource" to events.groupingBy { it.source ?: "[unknown]" }.eachCount(),
            "byMessage" to events.groupingBy { it.message }.eachCount().entries
                .sortedByDescending { it.value }
                .take(MAX_ANALYTICS_MESSAGES)
                .associate { it.key to it.value },
            "byPlugin" to events.groupingBy { it.plugin ?: "[unknown]" }.eachCount(),
            "errorCount" to events.count { it.errorType != null },
            "errorTypes" to events.mapNotNull { it.errorType }.groupingBy { it }.eachCount(),
            "statusCount" to statuses.size,
            "statusStates" to statuses.groupingBy { it.state }.eachCount(),
            "recent" to recentEvents(events),
            "oldestUtc" to events.minOfOrNull { it.timestamp }?.let(Instant::ofEpochMilli)?.toString(),
            "newestUtc" to events.maxOfOrNull { it.timestamp }?.let(Instant::ofEpochMilli)?.toString(),
            "activity" to activity(events),
        ) + attachmentFigures(attachments)
    }

    private fun recentEvents(events: List<Observation>): List<Map<String, Any?>> = events.takeLast(MAX_ANALYTICS_EVENTS).map { event ->
        linkedMapOf<String, Any?>(
            "id" to event.id,
            "timestampUtc" to Instant.ofEpochMilli(event.timestamp).toString(),
            "plugin" to (event.plugin ?: "[unknown]"),
            "source" to (event.source ?: "[unknown]"),
            "level" to event.level.name,
            "message" to event.message,
            "errorType" to event.errorType,
            "dataKeys" to event.data.keys.sorted(),
        )
    }

    private fun health(events: List<Observation>, statuses: List<ComponentStatus>): String = when {
        events.any { it.level == ObservationLevel.CRITICAL } -> "critical"
        events.any { it.level == ObservationLevel.ERROR } ||
            statuses.any { it.state.equals("failed", ignoreCase = true) || it.state.equals("degraded", ignoreCase = true) } -> "attention"
        else -> "healthy"
    }

    private fun attachmentFigures(attachments: List<StoredAttachment>): Map<String, Any?> = linkedMapOf(
        "attachmentCount" to attachments.size,
        "attachmentBytes" to attachments.sumOf { it.size },
        "attachmentsByContentType" to attachments.groupingBy { it.contentType }.eachCount(),
        "attachmentsByObservation" to attachments.groupingBy { it.observationId }.eachCount(),
        "attachmentSize" to linkedMapOf(
            "minBytes" to attachments.minOfOrNull { it.size },
            "maxBytes" to attachments.maxOfOrNull { it.size },
            "averageBytes" to attachments.map { it.size }.average().takeIf { attachments.isNotEmpty() },
        ),
    )

    private fun activity(events: List<Observation>): Map<String, Any?> {
        val now = System.currentTimeMillis()
        val window = events.filter { now - it.timestamp <= ANALYTICS_WINDOW_MS }
        val lastHour = events.filter { now - it.timestamp <= HOUR_MS }
        val lastDay = events.filter { now - it.timestamp <= DAY_MS }
        val windowErrors = window.count { it.errorType != null || it.level == ObservationLevel.ERROR || it.level == ObservationLevel.CRITICAL }
        return linkedMapOf(
            "windowMinutes" to ANALYTICS_WINDOW_MS / 60_000,
            "eventsLastWindow" to window.size,
            "errorsLastWindow" to windowErrors,
            "errorRateLastWindow" to windowErrors.toDouble() / window.size.coerceAtLeast(1),
            "activeSourcesLastWindow" to window.mapNotNull { it.source }.distinct().size,
            "activePluginsLastWindow" to window.mapNotNull { it.plugin }.distinct().size,
            "eventsLastHour" to lastHour.size,
            "errorsLastHour" to lastHour.count { it.errorType != null },
            "eventsLastDay" to lastDay.size,
            "errorsLastDay" to lastDay.count { it.errorType != null },
            "eventRatePerMinuteLastHour" to lastHour.size.toDouble() / 60.0,
            "errorRatePerMinuteLastDay" to lastDay.count { it.errorType != null }.toDouble() / (24 * 60.0),
        )
    }

    internal fun attachmentBytes(attachment: StoredAttachment): ByteArray = attachmentStore.read(attachment)

    internal fun clear() {
        journal.clear()
        attachmentStore.clear()
    }

    override fun close() = journal.close()

    private fun ObservationQuery.matches(observation: Observation): Boolean {
        val matchesPlugin = plugin == null || observation.plugin == plugin
        val matchesLevel = minimumLevel?.let { observation.level.ordinal >= it.ordinal } ?: true
        val matchesStart = since?.let { observation.timestamp >= it } ?: true
        val matchesEnd = until?.let { observation.timestamp <= it } ?: true
        return matchesPlugin && matchesLevel && matchesStart && matchesEnd
    }

    private companion object {
        const val MAX_ANALYTICS_EVENTS = 32
        const val HOUR_MS = 60 * 60_000L
        const val DAY_MS = 24 * HOUR_MS
        const val MAX_ANALYTICS_MESSAGES = 32
        const val ANALYTICS_WINDOW_MS = 5 * 60_000L
    }
}
