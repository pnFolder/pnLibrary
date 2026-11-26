package org.pnfolder.pnlibrary.core.link;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;

/** One server's report and when it arrived. */
public final class LinkEntry {
    private final String name;
    private final LinkReport report;
    private final Instant receivedAt;

    public LinkEntry(@NotNull String name, @NotNull LinkReport report, @NotNull Instant receivedAt) {
        this.name = name;
        this.report = report;
        this.receivedAt = receivedAt;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull LinkReport getReport() {
        return report;
    }

    public @NotNull Instant getReceivedAt() {
        return receivedAt;
    }
}
