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
    reportFactory: ((ObservabilityReportRequest) -> ObservabilityReport)? = null,
) : ObservabilityService {
    private val recordLock = Any()
    private val journal = ObservationJournal(dataFolder)
    private val attachmentStore = AttachmentStore(dataFolder)
    private val statusRegistry = ComponentStatusRegistry()
    @Volatile private var reportFactory = reportFactory

    init {
        val activeObservationIds = journal.applyRetention(clock())
        attachmentStore.removeOrphans(activeObservationIds)
    }

    override fun record(request: ObservationRequest): Observation = synchronized(recordLock) {
        val observation = Observation.from(request, clock())
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
}
