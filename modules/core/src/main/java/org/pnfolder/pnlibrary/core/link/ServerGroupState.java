package org.pnfolder.pnlibrary.core.link;

import java.util.Objects;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

/** How one server of a group compares with what the group asks for. */
public final class ServerGroupState {
    private final String server;
    private final NetworkServer known;
    private final List<String> missing;
    private final List<String> outdated;
    private final boolean customized;

    /**
     * @param server the server name
     * @param known the server's report, or null if it never reported
     * @param missing plugins the group asks for that the server does not have
     * @param outdated plugins of the group that have a newer version available on the server
     * @param customized whether the server differs from the rest of the group
     */
    public ServerGroupState(@NotNull String server, @Nullable NetworkServer known, @NotNull List<String> missing,
                            @NotNull List<String> outdated, boolean customized) {
        this.server = Objects.requireNonNull(server, "server");
        this.known = known;
        this.missing = Objects.requireNonNull(missing, "missing");
        this.outdated = Objects.requireNonNull(outdated, "outdated");
        this.customized = customized;
    }

    public @NotNull String getServer() {
        return server;
    }

    public @Nullable NetworkServer getKnown() {
        return known;
    }

    public @NotNull List<String> getMissing() {
        return missing;
    }

    public @NotNull List<String> getOutdated() {
        return outdated;
    }

    public boolean getCustomized() {
        return customized;
    }

    /** Whether the server is reachable and has everything, up to date. */
    public boolean getOk() {
        return known != null && known.getOnline() && missing.isEmpty() && outdated.isEmpty();
    }
}
