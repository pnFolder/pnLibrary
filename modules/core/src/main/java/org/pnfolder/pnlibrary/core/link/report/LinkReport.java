package org.pnfolder.pnlibrary.core.link.report;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

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

    /**
     * @param platform server software and version, for example {@code Paper 1.21.8}
     * @param libraryVersion the pnLibrary version
     * @param players how many players are online
     * @param plugins plugins registered with pnLibrary updates
     * @param sentAt when the report was made
     */
    public LinkReport(@NotNull String platform, @NotNull String libraryVersion, int players,
                      @NotNull List<LinkPlugin> plugins, @NotNull Instant sentAt) {
        this.platform = platform;
        this.libraryVersion = libraryVersion;
        this.players = players;
        this.plugins = plugins;
        this.sentAt = sentAt;
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
            && plugins.equals(r.plugins) && sentAt.equals(r.sentAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platform, libraryVersion, players, plugins, sentAt);
    }

    @Override
    public String toString() {
        return "LinkReport(platform=" + platform + ", libraryVersion=" + libraryVersion + ", players=" + players
            + ", plugins=" + plugins + ", sentAt=" + sentAt + ")";
    }
}
