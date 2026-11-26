package org.pnfolder.pnlibrary.core.link.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;

/** Builds the report a game server sends to the proxy. */
public final class LinkReports {
    private LinkReports() {
    }

    public static @NotNull LinkReport build(@NotNull PnLibrary library, @NotNull String platform, int players) {
        return build(library, platform, players, Instant.now());
    }

    /** Describes this server from {@code library}; {@code platform} names the server software, {@code players} counts who is online. */
    public static @NotNull LinkReport build(@NotNull PnLibrary library, @NotNull String platform, int players,
                                            @NotNull Instant now) {
        List<LinkPlugin> plugins = new ArrayList<>();
        for (UpdateRegistration registration : library.getUpdates().getProducts().all()) {
            UpdateSnapshot snapshot = registration.getSnapshot();
            String current = snapshot.getCurrentVersion();
            String latest = snapshot.getLatestVersion();
            plugins.add(new LinkPlugin(snapshot.getProduct(), current,
                latest != null && !latest.equals(current) ? latest : null, snapshot.getState().name()));
        }
        return new LinkReport(platform, library.getVersion(), players, plugins, now, StagedUpdate.waiting(library), null);
    }
}
