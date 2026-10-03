package ru.privatenull.pnlibrary.core.observability

import ru.privatenull.pnlibrary.api.observability.ComponentStatus
import ru.privatenull.pnlibrary.api.observability.Observation
import ru.privatenull.pnlibrary.api.observability.ObservationQuery
import ru.privatenull.pnlibrary.api.observability.ObservationRequest
import ru.privatenull.pnlibrary.api.observability.ObservabilityReport
import ru.privatenull.pnlibrary.api.observability.ObservabilityReportRequest
import ru.privatenull.pnlibrary.api.observability.ObservabilityService
import java.nio.file.Path

internal class ObservabilityRuntime(
    dataFolder: Path,
    private val clock: () -> Long = System::currentTimeMillis,
    private val reportFactory: ((ObservabilityReportRequest) -> ObservabilityReport)? = null,
) : ObservabilityService {
    private val recordLock = Any()
    private val journal = ObservationJournal(dataFolder)
    private val attachmentStore = AttachmentStore(dataFolder)
    private val statusRegistry = ComponentStatusRegistry()

    override fun record(request: ObservationRequest): Observation = synchronized(recordLock) {
        val observation = Observation.from(request, clock())
        request.files.forEach { file -> attachmentStore.save(observation.id, file) }

        val persisted = observation.copy(files = emptyList())
        journal.append(persisted)
        persisted
    }

    override fun recent(query: ObservationQuery): List<Observation> {
        val plugin = query.plugin
        val minimumLevel = query.minimumLevel
        val since = query.since
        val until = query.until
        return journal.recent()
            .filter { observation -> plugin == null || observation.plugin == plugin }
            .filter { observation -> minimumLevel == null || observation.level.ordinal >= minimumLevel.ordinal }
            .filter { observation -> since == null || observation.timestamp >= since }
            .filter { observation -> until == null || observation.timestamp <= until }
            .takeLast(query.limit.coerceAtLeast(0))
    }

    override fun status(status: ComponentStatus): ComponentStatus = statusRegistry.update(status)

    override fun createReport(request: ObservabilityReportRequest): ObservabilityReport {
        val factory = reportFactory ?: error("Observability report builder is not configured")
        return factory(request)
    }

    internal fun statuses(): List<ComponentStatus> = statusRegistry.snapshot()

    internal fun attachments(observationId: String): List<StoredAttachment> =
        attachmentStore.forObservation(observationId)

    override fun close() = journal.close()
}
