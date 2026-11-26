package org.pnfolder.pnlibrary.core.menu.screens;

import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels;

/** What the network screen shows about one server, counted from its report. */
final class LinkReportView {
    private final NetworkServer server;
    private final int updates;
    private final int problems;

    LinkReportView(NetworkServer server) {
        this.server = server;
        int available = 0;
        int failing = 0;
        for (LinkPlugin plugin : server.getReport().getPlugins()) {
            if (plugin.getState().equals(UpdateState.UPDATE_AVAILABLE.name())) {
                available++;
            }
            if (isProblem(plugin.getState())) {
                failing++;
            }
        }
        this.updates = available;
        this.problems = failing;
    }

    private static boolean isProblem(String state) {
        try {
            return CatalogLabels.PROBLEMS.contains(UpdateState.valueOf(state));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    private LinkReport report() {
        return server.getReport();
    }

    String platform() {
        return report().getPlatform();
    }

    String libraryVersion() {
        return report().getLibraryVersion();
    }

    int players() {
        return report().getPlayers();
    }

    int pluginCount() {
        return report().getPlugins().size();
    }

    int updates() {
        return updates;
    }

    int problems() {
        return problems;
    }

    String material() {
        if (!server.getOnline()) {
            return "BARRIER";
        }
        if (problems > 0) {
            return "REDSTONE_BLOCK";
        }
        return updates > 0 ? "GOLD_BLOCK" : "EMERALD_BLOCK";
    }
}
