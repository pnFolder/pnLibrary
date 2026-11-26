package org.pnfolder.pnlibrary.core.link.report;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.ServerGroup;

/**
 * The map of the network the proxy sends to every game server: its own report and the latest report of each
 * server. A game server shows it in its menu, so any server can be used to look at the whole network.
 */
public final class NetworkReport {
    private final LinkReport proxy;
    private final List<NetworkServer> servers;
    private final Instant sentAt;
    private final List<ServerGroup> groups;

    public NetworkReport(@NotNull LinkReport proxy, @NotNull List<NetworkServer> servers, @NotNull Instant sentAt) {
        this(proxy, servers, sentAt, Collections.<ServerGroup>emptyList());
    }

    /**
     * @param proxy the proxy's own report
     * @param servers the game servers that reported to the proxy
     * @param sentAt when the map was made
     * @param groups the groups of servers kept on the proxy
     */
    public NetworkReport(@NotNull LinkReport proxy, @NotNull List<NetworkServer> servers, @NotNull Instant sentAt,
                         @NotNull List<ServerGroup> groups) {
        this.proxy = proxy;
        this.servers = servers;
        this.sentAt = sentAt;
        this.groups = groups;
    }

    public @NotNull LinkReport getProxy() {
        return proxy;
    }

    public @NotNull List<NetworkServer> getServers() {
        return servers;
    }

    public @NotNull Instant getSentAt() {
        return sentAt;
    }

    public @NotNull List<ServerGroup> getGroups() {
        return groups;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NetworkReport)) {
            return false;
        }
        NetworkReport r = (NetworkReport) other;
        return proxy.equals(r.proxy) && servers.equals(r.servers) && sentAt.equals(r.sentAt) && groups.equals(r.groups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proxy, servers, sentAt, groups);
    }

    @Override
    public String toString() {
        return "NetworkReport(proxy=" + proxy + ", servers=" + servers + ", sentAt=" + sentAt + ", groups=" + groups + ")";
    }
}
