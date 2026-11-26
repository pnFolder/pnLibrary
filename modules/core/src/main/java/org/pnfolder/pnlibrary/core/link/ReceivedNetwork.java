package org.pnfolder.pnlibrary.core.link;

import java.util.Objects;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;

/** A network map and when this server received it. */
public final class ReceivedNetwork {
    private final NetworkReport report;
    private final Instant receivedAt;

    public ReceivedNetwork(@NotNull NetworkReport report, @NotNull Instant receivedAt) {
        this.report = Objects.requireNonNull(report, "report");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
    }

    public @NotNull NetworkReport getReport() {
        return report;
    }

    public @NotNull Instant getReceivedAt() {
        return receivedAt;
    }
}
