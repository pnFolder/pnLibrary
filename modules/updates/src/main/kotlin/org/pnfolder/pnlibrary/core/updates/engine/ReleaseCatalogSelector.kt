package org.pnfolder.pnlibrary.core.updates.engine

import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.updates.UpdateChannel

/** Deterministically selects the newest catalog release compatible with a runtime. */
internal object ReleaseCatalogSelector {
    /** Selects the newest channel, platform, Java, and API-compatible release. */
    fun select(
        catalog: ReleaseCatalog,
        acceptedChannel: UpdateChannel,
        platform: PlatformType,
        javaFeature: Int,
        apiVersion: Int,
    ): CatalogRelease? = catalog.releases
        .asSequence()
        .filter { acceptedChannel.accepts(it.channel) }
        .filter { it.api.supports(apiVersion) }
        .filter { it.artifacts.any { artifact -> artifact.matchesPlatform(platform) && artifact.supports(javaFeature) } }
        .sortedByDescending { it.version }
        .firstOrNull()

    private fun CatalogArtifact.supports(javaFeature: Int): Boolean =
        javaFeature >= javaMinimum && (javaMaximum == null || javaFeature <= javaMaximum)
}

/** Whether this artifact runs on [target]: an exact platform match, or one built for every platform. */
internal fun CatalogArtifact.matchesPlatform(target: PlatformType): Boolean =
    platform == target || platform == PlatformType.UNIVERSAL
