package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ProductRelease
import ru.privatenull.pnlibrary.api.updates.UpdateChannel

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
