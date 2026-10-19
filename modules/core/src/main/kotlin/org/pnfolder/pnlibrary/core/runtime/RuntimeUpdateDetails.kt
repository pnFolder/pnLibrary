package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration

/** Stable diagnostic representation for one resolved update plan. */
internal object RuntimeUpdateDetails {
    fun registration(value: UpdateRegistration): Map<String, Any?> {
        val snapshot = value.snapshot
        return linkedMapOf(
            "product" to snapshot.product,
            "repository" to value.repository,
            "currentVersion" to snapshot.currentVersion,
            "latestVersion" to snapshot.latestVersion,
            "channel" to snapshot.channel.name,
            "state" to snapshot.state.name,
            "currentJava" to snapshot.currentJava,
            "requiredJava" to snapshot.requiredJava,
            "automaticDownload" to snapshot.automaticDownload,
            "releaseUrl" to snapshot.releaseUrl,
            "message" to snapshot.message,
            "availableReleases" to snapshot.availableReleases.map { release ->
                linkedMapOf<String, Any?>(
                    "version" to release.version,
                    "channel" to release.channel.name,
                    "publishedAt" to release.publishedAt?.toString(),
                )
            },
        )
    }

    fun of(snapshot: UpdatePlanSnapshot): Map<String, Any?> = linkedMapOf(
        "id" to snapshot.id.toString(),
        "revision" to snapshot.revision,
        "state" to snapshot.state.name,
        "message" to snapshot.message,
        "blockers" to snapshot.blockers.map { it.javaClass.simpleName },
        "targetApi" to snapshot.plan?.targetApi,
        "changeCount" to snapshot.plan?.changes?.size,
        "selectedReleaseCount" to snapshot.plan?.selected?.size,
    )
}
