package org.pnfolder.pnlibrary.core.link.wire;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;

/** A server report. */
public final class ReportFrame implements LinkFrame {
    private final LinkReport report;

    public ReportFrame(@NotNull LinkReport report) {
        this.report = report;
    }

    public @NotNull LinkReport getReport() {
        return report;
    }
}
