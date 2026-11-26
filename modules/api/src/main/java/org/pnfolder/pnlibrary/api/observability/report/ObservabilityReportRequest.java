package org.pnfolder.pnlibrary.api.observability.report;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Controls which support data is included in an observability report. */
public final class ObservabilityReportRequest {
    private final String target;
    private final boolean includeLogs;
    private final boolean includeConfigurations;

    /** A report of every plugin with logs and configurations. */
    public ObservabilityReportRequest() {
        this("all", true, true);
    }

    /**
     * Creates a request.
     *
     * @param target plugin name to collect, or {@code all} for every registered plugin
     * @param includeLogs whether the bounded runtime log buffer is included
     * @param includeConfigurations whether registered sanitized configurations are included
     */
    public ObservabilityReportRequest(@NotNull String target, boolean includeLogs, boolean includeConfigurations) {
        this.target = Objects.requireNonNull(target);
        this.includeLogs = includeLogs;
        this.includeConfigurations = includeConfigurations;
    }

    /** The plugin name to collect, or {@code all}. */
    @NotNull
    public String getTarget() {
        return target;
    }

    /** Whether the bounded runtime log buffer is included. */
    public boolean isIncludeLogs() {
        return includeLogs;
    }

    /** Whether registered sanitized configurations are included. */
    public boolean isIncludeConfigurations() {
        return includeConfigurations;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ObservabilityReportRequest)) {
            return false;
        }
        ObservabilityReportRequest that = (ObservabilityReportRequest) other;
        return includeLogs == that.includeLogs && includeConfigurations == that.includeConfigurations && target.equals(that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, includeLogs, includeConfigurations);
    }

    @NotNull
    @Override
    public String toString() {
        return "ObservabilityReportRequest(target=" + target + ", includeLogs=" + includeLogs
                + ", includeConfigurations=" + includeConfigurations + ")";
    }
}
