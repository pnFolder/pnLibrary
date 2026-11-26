package org.pnfolder.pnlibrary.updates.policy;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;

/** A product's releases and the newest one its channel allows. */
public final class ReleaseChannelSelection {
    private final List<ProductRelease> all;
    private final ProductRelease latestAllowed;

    /** Creates the selection. */
    public ReleaseChannelSelection(@NotNull List<ProductRelease> all, @Nullable ProductRelease latestAllowed) {
        this.all = Objects.requireNonNull(all, "all");
        this.latestAllowed = latestAllowed;
    }

    /** Every release of the product. */
    @NotNull
    public List<ProductRelease> getAll() {
        return all;
    }

    /** The newest release the channel allows, or null when none is allowed. */
    @Nullable
    public ProductRelease getLatestAllowed() {
        return latestAllowed;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ReleaseChannelSelection)) {
            return false;
        }
        ReleaseChannelSelection that = (ReleaseChannelSelection) other;
        return all.equals(that.all) && Objects.equals(latestAllowed, that.latestAllowed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(all, latestAllowed);
    }

    @Override
    public String toString() {
        return "ReleaseChannelSelection(all=" + all + ", latestAllowed=" + latestAllowed + ")";
    }
}
