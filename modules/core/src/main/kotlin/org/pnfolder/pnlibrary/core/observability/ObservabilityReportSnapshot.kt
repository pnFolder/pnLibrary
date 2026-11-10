package org.pnfolder.pnlibrary.core.observability

import com.google.gson.GsonBuilder
import org.pnfolder.pnlibrary.api.observability.Observation
import org.pnfolder.pnlibrary.api.observability.ObservationKeys
import org.pnfolder.pnlibrary.api.observability.ObservationQuery
import java.nio.charset.StandardCharsets
import java.time.Instant

/** Observation journal, attachments, statuses and analytics captured for one support report. */
internal data class ObservabilityReportSnapshot(
    val journal: ByteArray,
    val attachmentManifest: ByteArray,
    val attachments: Map<String, ByteArray>,
    val statuses: List<Map<String, Any?>> = emptyList(),
    val analytics: Map<String, Any?> = emptyMap(),
)

internal fun ObservabilityRuntime.reportSnapshot(): ObservabilityReportSnapshot {
    val gson = GsonBuilder().disableHtmlEscaping().create()
    val observations = recent(ObservationQuery(limit = Int.MAX_VALUE))
    val journal = observations
        .joinToString(separator = "\n", postfix = "\n") { gson.toJson(it.reportLine()) }
        .toByteArray(StandardCharsets.UTF_8)

    val storedAttachments = allAttachments()
    val manifestEntries = storedAttachments.map { attachment -> attachment.manifestEntry() }
    val manifest = gson.toJson(manifestEntries).toByteArray(StandardCharsets.UTF_8)

    return ObservabilityReportSnapshot(
        journal = journal,
        attachmentManifest = manifest,
        attachments = storedAttachments.associate { attachment ->
            attachment.id to attachmentBytes(attachment)
        },
        analytics = analytics(),
        statuses = statuses().map { status ->
            linkedMapOf<String, Any?>(
                "plugin" to status.plugin,
                "component" to status.component,
                "state" to status.state,
                "detail" to status.detail,
                "data" to status.data,
                "updatedAt" to status.updatedAt,
            )
        },
    )
}

private fun StoredAttachment.manifestEntry(): Map<String, Any> = linkedMapOf(
    "id" to id,
    "eventId" to observationId,
    "name" to originalName,
    "contentType" to contentType,
    "size" to size,
    "sha256" to sha256,
)

/** The stored observation with readable UTC times, so support sees when it happened and when it expires. */
internal fun Observation.reportLine(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "timestamp" to timestamp,
    "timeUtc" to Instant.ofEpochMilli(timestamp).toString(),
    "expiresUtc" to data[ObservationKeys.KEEP_UNTIL_MS]?.toLongOrNull()?.let(Instant::ofEpochMilli)?.toString(),
    "plugin" to plugin,
    "source" to source,
    "level" to level,
    "message" to message,
    "data" to data - ObservationKeys.KEEP_UNTIL_MS,
    "errorType" to errorType,
    "errorMessage" to errorMessage,
)
