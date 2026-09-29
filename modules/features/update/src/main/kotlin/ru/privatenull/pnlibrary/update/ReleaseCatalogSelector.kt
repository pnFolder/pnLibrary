package ru.privatenull.pnlibrary.update

import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.UpdateChannel

/** Deterministically selects the newest catalog release compatible with a runtime. */
object ReleaseCatalogSelector {
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
        .filter { it.artifacts.any { artifact -> artifact.platform == platform && artifact.supports(javaFeature) } }
        .sortedByDescending { it.version }
        .firstOrNull()

    private fun CatalogArtifact.supports(javaFeature: Int): Boolean =
        javaFeature >= javaMinimum && (javaMaximum == null || javaFeature <= javaMaximum)
}
