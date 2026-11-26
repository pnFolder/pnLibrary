package org.pnfolder.pnlibrary.core.link.report;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One plugin as a game server reports it to the proxy. */
public final class LinkPlugin {
    private final String product;
    private final String version;
    private final String latest;
    private final String state;

    /**
     * @param product the plugin id
     * @param version the installed version
     * @param latest newest known version, or null when it is the installed one
     * @param state update state word, for example {@code UP_TO_DATE} or {@code UPDATE_AVAILABLE}
     */
    public LinkPlugin(@NotNull String product, @NotNull String version, @Nullable String latest, @NotNull String state) {
        this.product = product;
        this.version = version;
        this.latest = latest;
        this.state = state;
    }

    public @NotNull String getProduct() {
        return product;
    }

    public @NotNull String getVersion() {
        return version;
    }

    public @Nullable String getLatest() {
        return latest;
    }

    public @NotNull String getState() {
        return state;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LinkPlugin)) {
            return false;
        }
        LinkPlugin p = (LinkPlugin) other;
        return product.equals(p.product) && version.equals(p.version) && Objects.equals(latest, p.latest)
            && state.equals(p.state);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, version, latest, state);
    }

    @Override
    public String toString() {
        return "LinkPlugin(product=" + product + ", version=" + version + ", latest=" + latest + ", state=" + state + ")";
    }
}
