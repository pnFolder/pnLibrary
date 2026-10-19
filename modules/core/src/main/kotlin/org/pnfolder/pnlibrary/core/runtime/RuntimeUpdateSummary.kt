package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import java.time.Duration
import java.time.Instant

/** Aggregates update registrations into the stable runtime diagnostic summary. */
internal object RuntimeUpdateSummary {
    fun of(registrations: List<UpdateRegistration>): Map<String, Any?> {
        val latestPublishedAtByChannel: Map<String, Instant> = registrations
            .flatMap { it.snapshot.availableReleases }
            .mapNotNull { release -> release.publishedAt?.let { release.channel.name to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, publishedAt) -> publishedAt.max() }

        return linkedMapOf(
            "count" to registrations.size,
            "byChannel" to registrations.groupingBy { it.snapshot.channel.name }.eachCount(),
            "byState" to registrations.groupingBy { it.snapshot.state.name }.eachCount(),
            "availableCount" to registrations.count { it.snapshot.latestVersion != null },
            "automaticDownloadCount" to registrations.count { it.snapshot.automaticDownload },
            "javaIncompatibleCount" to registrations.count {
                it.snapshot.state.name.contains("JAVA", ignoreCase = true) ||
                    it.snapshot.message?.contains("java", ignoreCase = true) == true
            },
            "releaseCountByProduct" to registrations.associate {
                it.snapshot.product to it.snapshot.availableReleases.size
            },
            "releaseCountByChannel" to registrations
                .flatMap { it.snapshot.availableReleases }
                .groupingBy { it.channel.name }
                .eachCount(),
            "latestPublishedAtByChannel" to latestPublishedAtByChannel.mapValues { (_, publishedAt) -> publishedAt.toString() },
            "releaseAgeDaysByChannel" to latestPublishedAtByChannel.mapValues { (_, publishedAt) ->
                Duration.between(publishedAt, Instant.now()).toDays().coerceAtLeast(0)
            },
            "staleChannelCount" to latestPublishedAtByChannel.count { (_, publishedAt) ->
                Duration.between(publishedAt, Instant.now()).toDays() >= 30
            },
            "messages" to registrations
                .mapNotNull { it.snapshot.message?.take(256) }
                .groupingBy { it }
                .eachCount(),
        )
    }
}
