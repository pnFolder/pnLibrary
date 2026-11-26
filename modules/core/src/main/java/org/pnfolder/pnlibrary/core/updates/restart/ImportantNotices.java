package org.pnfolder.pnlibrary.core.updates.restart;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.link.LinkEntry;
import org.pnfolder.pnlibrary.core.link.ReceivedNetwork;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

/**
 * The important announcements of the network: every proxy or server that reports a staged update waiting for a
 * restart. They are built from the latest network map, so they disappear by themselves once the update is applied.
 */
public final class ImportantNotices {
    /** Longer than the network map is announced, so one missed map does not hide the notices. */
    static final Duration FRESH = Duration.ofMinutes(3);
    private static final String PROXY = "Прокси";
    private static final String PROXY_TEXT = "Нужен перезапуск прокси, чтобы подготовленные обновления применились.";
    private static final String SERVER_TEXT = "Нужен перезапуск сервера, чтобы подготовленные обновления применились.";

    private ImportantNotices() {
    }

    /** The notices of {@code network} as of {@code now}; none when the map is missing or too old to trust. */
    public static @NotNull List<ImportantNotice> from(@Nullable ReceivedNetwork network, @NotNull Instant now) {
        List<ImportantNotice> notices = new ArrayList<>();
        if (network == null || Duration.between(network.getReceivedAt(), now).compareTo(FRESH) > 0) {
            return notices;
        }
        add(notices, PROXY, network.getReport().getProxy(), PROXY_TEXT);
        for (NetworkServer server : network.getReport().getServers()) {
            if (server.getOnline()) {
                add(notices, server.getName(), server.getReport(),
                    SERVER_TEXT);
            }
        }
        return notices;
    }

    /** The notices a proxy builds from its own state and the reports of the game servers it heard from. */
    public static @NotNull List<ImportantNotice> fromReports(boolean proxyWaiting, @NotNull List<LinkEntry> entries,
                                                             @NotNull Instant now) {
        List<ImportantNotice> notices = new ArrayList<>();
        if (proxyWaiting) {
            notices.add(new ImportantNotice(PROXY, null, PROXY_TEXT));
        }
        for (LinkEntry entry : entries) {
            if (Duration.between(entry.getReceivedAt(), now).compareTo(FRESH) <= 0) {
                add(notices, entry.getName(), entry.getReport(), SERVER_TEXT);
            }
        }
        return notices;
    }

    private static void add(List<ImportantNotice> notices, String name, LinkReport report, String text) {
        if (report.getRestartNeeded()) {
            notices.add(new ImportantNotice(name, report.getAddress(), text));
        }
    }
}
