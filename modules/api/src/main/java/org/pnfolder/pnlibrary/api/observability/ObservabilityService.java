package org.pnfolder.pnlibrary.api.observability;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationQuery;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationRequest;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationScope;
import org.pnfolder.pnlibrary.api.observability.report.ComponentStatus;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReport;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReportRequest;

import java.util.List;
import java.util.function.Consumer;

/**
 * Records operational history, tracks current component state, and creates support reports from one consistent data
 * source.
 *
 * <p>Calls are safe from platform threads. Closing the service releases its persistence resources and rejects
 * subsequent write operations.
 */
@ApiStatus.NonExtendable
public interface ObservabilityService extends AutoCloseable {
    /** Records one immutable request and persists any declared attachments. */
    @NotNull
    Observation record(@NotNull ObservationRequest request);

    /** Builds and records one observation: {@code service.capture(scope -> scope.message("Done").data("n", 3))}. */
    @NotNull
    default Observation capture(@NotNull Consumer<ObservationScope> block) {
        ObservationScope scope = new ObservationScope();
        block.accept(scope);
        return record(scope.build());
    }

    /**
     * Builds an error observation and captures {@code error} immediately.
     *
     * <p>The resulting level is always {@code ERROR}.
     */
    @NotNull
    default Observation failure(@NotNull Throwable error, @NotNull Consumer<ObservationScope> block) {
        ObservationScope scope = new ObservationScope();
        block.accept(scope);
        return record(scope.build(error));
    }

    /** Like the call with a block, with nothing to add. */
    @NotNull
    default Observation failure(@NotNull Throwable error) {
        return record(new ObservationScope().build(error));
    }

    /** Returns observations matching {@code query}, ordered from newest to oldest. */
    @NotNull
    List<Observation> recent(@NotNull ObservationQuery query);

    /** Returns the newest 50 observations of every plugin. */
    @NotNull
    default List<Observation> recent() {
        return recent(new ObservationQuery());
    }

    /** Stores and returns the latest state for one plugin component. */
    @NotNull
    ComponentStatus status(@NotNull ComponentStatus status);

    /** Builds a report of the current state of the monitored components, filtered by {@code request}. */
    @NotNull
    ObservabilityReport createReport(@NotNull ObservabilityReportRequest request);

    /** Builds a report of every plugin with logs and configurations. */
    @NotNull
    default ObservabilityReport createReport() {
        return createReport(new ObservabilityReportRequest());
    }

    /** Releases the persistence resources of the service. */
    @Override
    void close();
}
