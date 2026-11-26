package org.pnfolder.pnlibrary.updates.policy;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;

/** Builds the release view for one product using the channel chosen in configuration. */
public final class ReleaseChannelSelector {
    private ReleaseChannelSelector() {
    }

    /** Picks the releases of {@code product} the channel allows. */
    @NotNull
    public static ReleaseChannelSelection select(@NotNull ProductId product, @NotNull List<ProductRelease> releases,
                                                 @NotNull UpdateChannel channel) {
        List<ProductRelease> productReleases = new ArrayList<>();
        ProductRelease latestAllowed = null;
        for (ProductRelease release : releases) {
            if (!release.getProduct().equals(product)) {
                continue;
            }
            productReleases.add(release);
            if (channel.accepts(release.getChannel())
                && (latestAllowed == null || release.getVersion().compareTo(latestAllowed.getVersion()) > 0)) {
                latestAllowed = release;
            }
        }
        return new ReleaseChannelSelection(productReleases, latestAllowed);
    }
}
