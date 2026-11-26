package org.pnfolder.pnlibrary.core.observability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsContributor;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;
import org.pnfolder.pnlibrary.api.observability.ObservabilityService;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationQuery;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationRequest;
import org.pnfolder.pnlibrary.api.observability.report.ComponentStatus;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReport;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReportRequest;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * {@code PnLibrary.diagnostics} and {@code PnLibrary.observability} are this one object seen through two interfaces.
 * Storage and retention stay inside {@link ObservabilityRuntime}.
 */
public final class ObservabilityHub implements DiagnosticsService, ObservabilityService {
    private final DiagnosticsRegistry diagnostics;
    private final ObservabilityRuntime runtime;

    public ObservabilityHub(@NotNull DiagnosticsRegistry diagnostics, @NotNull ObservabilityRuntime runtime) {
        this.diagnostics = diagnostics;
        this.runtime = runtime;
    }

    @NotNull
    @Override
    public DiagnosticRegistration register(@NotNull String plugin, @NotNull DiagnosticsContributor contributor) {
        return diagnostics.register(plugin, contributor);
    }

    @NotNull
    @Override
    public DiagnosticRegistration register(@NotNull String plugin, @NotNull Path dataDirectory,
                                           @NotNull DiagnosticsContributor contributor) {
        return diagnostics.register(plugin, dataDirectory, contributor);
    }

    @Override
    public void status(@NotNull String plugin, @NotNull String component, @NotNull String state, @NotNull String detail,
                       @NotNull Map<String, Object> fields) {
        diagnostics.status(plugin, component, state, detail, fields);
        runtime.status(new ComponentStatus(plugin, component, state, detail, ObservationConversions.toStringValues(fields)));
    }

    @Override
    public void clearStatus(@NotNull String plugin, @NotNull String component) {
        diagnostics.clearStatus(plugin, component);
    }

    @Override
    public void clearPlugin(@NotNull String plugin) {
        diagnostics.clearPlugin(plugin);
    }

    @Override
    public void record(@NotNull String plugin, @NotNull DiagnosticLevel level, @NotNull String component,
                       @NotNull String code, @NotNull String message, @Nullable Throwable error,
                       @NotNull Map<String, Object> fields) {
        diagnostics.record(plugin, level, component, code, message, error, fields);
    }

    @NotNull
    @Override
    public Observation record(@NotNull ObservationRequest request) {
        return runtime.record(request);
    }

    @NotNull
    @Override
    public List<Observation> recent(@NotNull ObservationQuery query) {
        return runtime.recent(query);
    }

    @NotNull
    @Override
    public ComponentStatus status(@NotNull ComponentStatus status) {
        return runtime.status(status);
    }

    @NotNull
    @Override
    public ObservabilityReport createReport(@NotNull ObservabilityReportRequest request) {
        return runtime.createReport(request);
    }

    @Override
    public void close() {
        runtime.close();
    }
}
