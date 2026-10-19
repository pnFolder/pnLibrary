package org.pnfolder.pnlibrary.core.updates.policy

import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.ProductRelease
import org.pnfolder.pnlibrary.api.updates.UpdateChannel

/** A product's releases and the newest one its channel allows. */
internal data class ReleaseChannelSelection(
    val all: List<ProductRelease>,
    val latestAllowed: ProductRelease?,
)

/** Builds the release view for one product using the channel chosen in configuration. */
internal object ReleaseChannelSelector {
    fun select(
        product: ProductId,
        releases: List<ProductRelease>,
        channel: UpdateChannel,
    ): ReleaseChannelSelection {
        val productReleases = releases.filter { it.product == product }
        val latestAllowed = productReleases
            .filter { release -> channel.accepts(release.channel) }
            .maxByOrNull(ProductRelease::version)

        return ReleaseChannelSelection(productReleases, latestAllowed)
    }
}
