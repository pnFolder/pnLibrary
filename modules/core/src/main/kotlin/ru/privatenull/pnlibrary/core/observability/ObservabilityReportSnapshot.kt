package ru.privatenull.pnlibrary.core.observability

import com.google.gson.GsonBuilder
import ru.privatenull.pnlibrary.api.observability.ObservationQuery
import java.nio.charset.StandardCharsets

internal data class ObservabilityReportSnapshot(
    val journal: ByteArray,
    val attachmentManifest: ByteArray,
    val attachments: Map<String, ByteArray>,
)

internal fun ObservabilityRuntime.reportSnapshot(): ObservabilityReportSnapshot {
    val gson = GsonBuilder().disableHtmlEscaping().create()
    val journal = recent(ObservationQuery(limit = Int.MAX_VALUE))
        .joinToString(separator = "\n", postfix = "\n") { gson.toJson(it) }
        .toByteArray(StandardCharsets.UTF_8)
    val storedAttachments = allAttachments()
    val manifest = gson.toJson(storedAttachments.map {
        mapOf("id" to it.id, "eventId" to it.observationId, "name" to it.originalName,
            "contentType" to it.contentType, "size" to it.size, "sha256" to it.sha256)
    }).toByteArray(StandardCharsets.UTF_8)
    return ObservabilityReportSnapshot(
        journal = journal,
        attachmentManifest = manifest,
        attachments = storedAttachments.associate { it.id to attachmentBytes(it) },
    )
}
