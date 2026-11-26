package org.pnfolder.pnlibrary.core.link.report;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a game server tells the proxy about itself: the platform, the pnLibrary version, the players and the plugins
 * that use pnLibrary updates. It carries no commands: the link only reports.
 */
public final class LinkReport {
    private final String platform;
    private final String libraryVersion;
    private final int players;
    private final List<LinkPlugin> plugins;
    private final Instant sentAt;
    private final boolean restartNeeded;
    private final String address;

    /**
     * @param platform server software and version, for example {@code Paper 1.21.8}
     * @param libraryVersion the pnLibrary version
     * @param players how many players are online
     * @param plugins plugins registered with pnLibrary updates
     * @param sentAt when the report was made
     */
    public LinkReport(@NotNull String platform, @NotNull String libraryVersion, int players,
                      @NotNull List<LinkPlugin> plugins, @NotNull Instant sentAt) {
        this(platform, libraryVersion, players, plugins, sentAt, false, null);
    }

    /**
     * @param restartNeeded whether an update is staged and waits for a restart of this server
     * @param address where the server can be reached, as the proxy knows it; null when unknown
     */
    public LinkReport(@NotNull String platform, @NotNull String libraryVersion, int players,
                      @NotNull List<LinkPlugin> plugins, @NotNull Instant sentAt, boolean restartNeeded,
                      @Nullable String address) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.libraryVersion = Objects.requireNonNull(libraryVersion, "libraryVersion");
        this.players = players;
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
        this.restartNeeded = restartNeeded;
        this.address = address;
    }

    public @NotNull String getPlatform() {
        return platform;
    }

    public @NotNull String getLibraryVersion() {
        return libraryVersion;
    }

    public int getPlayers() {
        return players;
    }

    public @NotNull List<LinkPlugin> getPlugins() {
        return plugins;
    }

    public @NotNull Instant getSentAt() {
        return sentAt;
    }

    /** Whether a staged update waits for this server to restart. */
    public boolean getRestartNeeded() {
        return restartNeeded;
    }

    /** Where the server can be reached, or null when the proxy did not say. */
    public @Nullable String getAddress() {
        return address;
    }

    /** The same report with the address the proxy knows the server by. */
    public @NotNull LinkReport withAddress(@Nullable String value) {
        return new LinkReport(platform, libraryVersion, players, plugins, sentAt, restartNeeded, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LinkReport)) {
            return false;
        }
        LinkReport r = (LinkReport) other;
        return players == r.players && platform.equals(r.platform) && libraryVersion.equals(r.libraryVersion)
            && plugins.equals(r.plugins) && sentAt.equals(r.sentAt) && restartNeeded == r.restartNeeded
            && Objects.equals(address, r.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platform, libraryVersion, players, plugins, sentAt, restartNeeded, address);
    }

    @Override
    public String toString() {
        return "LinkReport(platform=" + platform + ", libraryVersion=" + libraryVersion + ", players=" + players
            + ", plugins=" + plugins + ", sentAt=" + sentAt + ", restartNeeded=" + restartNeeded + ", address=" + address + ")";
    }
}
