package org.pnfolder.pnlibrary.core.link.report;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** One game server in the map of the network, as the proxy knows it. */
public final class NetworkServer {
    private final String name;
    private final boolean online;
    private final LinkReport report;

    /**
     * @param name the name the proxy knows the server under
     * @param online whether the server reported recently
     * @param report its latest report
     */
    public NetworkServer(@NotNull String name, boolean online, @NotNull LinkReport report) {
        this.name = Objects.requireNonNull(name, "name");
        this.online = online;
        this.report = Objects.requireNonNull(report, "report");
    }

    public @NotNull String getName() {
        return name;
    }

    public boolean getOnline() {
        return online;
    }

    public @NotNull LinkReport getReport() {
        return report;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NetworkServer)) {
            return false;
        }
        NetworkServer s = (NetworkServer) other;
        return online == s.online && name.equals(s.name) && report.equals(s.report);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, online, report);
    }

    @Override
    public String toString() {
        return "NetworkServer(name=" + name + ", online=" + online + ", report=" + report + ")";
    }
}
