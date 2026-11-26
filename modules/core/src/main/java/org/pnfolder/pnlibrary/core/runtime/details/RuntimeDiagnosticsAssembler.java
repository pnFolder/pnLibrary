package org.pnfolder.pnlibrary.core.runtime.details;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRegistry;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.core.metrics.session.MetricsSessions;
import org.pnfolder.pnlibrary.core.observability.SupportRuntime;
import org.pnfolder.pnlibrary.core.tasks.TaskService;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

/** Collects the runtime state of the library into a diagnostics report. */
public final class RuntimeDiagnosticsAssembler {
    private final String version;
    private final BooleanSupplier closed;
    private final PlatformIdentity identity;
    private final Path dataFolder;
    private final PnLibraryConfig config;
    private final TaskService tasks;
    private final UpdateService updates;
    private final DiagnosticsRegistry diagnostics;
    private final SupportRuntime support;
    private final MetricsSessions metrics;
    private final CommandRegistry commands;
    private final Supplier<Map<String, Object>> journal;

    public RuntimeDiagnosticsAssembler(String version, BooleanSupplier closed, PlatformIdentity identity, Path dataFolder,
                                       PnLibraryConfig config, TaskService tasks, UpdateService updates,
                                       DiagnosticsRegistry diagnostics, SupportRuntime support, MetricsSessions metrics,
                                       CommandRegistry commands, Supplier<Map<String, Object>> journal) {
        this.version = version;
        this.closed = closed;
        this.identity = identity;
        this.dataFolder = dataFolder;
        this.config = config;
        this.tasks = tasks;
        this.updates = updates;
        this.diagnostics = diagnostics;
        this.support = support;
        this.metrics = metrics;
        this.commands = commands;
        this.journal = journal;
    }

    public @NotNull Map<String, Object> assemble() {
        List<TaskSnapshot> taskSnapshots = tasks.query();
        List<UpdateRegistration> updateSnapshots = updates.getProducts().all();
        int taskFailures = failedTasks(taskSnapshots);
        int failedUpdates = failedUpdates(updateSnapshots);
        Map<String, Object> diagnosticSummary = diagnostics.diagnosticSummary();
        Map<String, Object> history = diagnostics.historyAnalytics();
        Map<String, Object> nativeLogs = support.logSummary();
        Map<String, Object> reports = support.reportSummary();
        Map<String, Object> observability = support.observabilityAnalytics();
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("library", library());
        report.put("journal", journalSummary());
        report.put("tasks", RuntimeTaskDetails.entries(taskSnapshots));
        report.put("taskSummary", RuntimeTaskDetails.summary(taskSnapshots));
        report.put("updates", registrations(updateSnapshots));
        report.put("updateSummary", RuntimeUpdateSummary.of(updateSnapshots));
        report.put("analytics", RuntimeAnalytics.of(taskSnapshots.size(), updateSnapshots.size(), taskFailures, failedUpdates,
                diagnosticSummary, history, nativeLogs, reports, observability));
        addRuntimeSections(report, nativeLogs, reports, observability);
        return report;
    }

    private void addRuntimeSections(Map<String, Object> report, Map<String, Object> nativeLogs, Map<String, Object> reports,
                                    Map<String, Object> observability) {
        report.put("metrics", metrics.diagnosticSnapshot());
        report.put("metricsAnalytics", metrics.diagnosticAnalytics());
        report.put("commandsAnalytics", commands.diagnosticSnapshot());
        report.put("diagnostics", diagnostics.diagnosticSummary());
        report.put("diagnosticHistory", diagnostics.historyAnalytics());
        report.put("nativeLogs", nativeLogs);
        report.put("reports", reports);
        report.put("observability", observability);
        report.put("configurationDeclarations", configurationDeclarations());
        report.put("updateGraph", updateGraph());
    }

    private static int failedTasks(List<TaskSnapshot> tasks) {
        int failed = 0;
        for (TaskSnapshot task : tasks) {
            failed += task.getLastFailure() != null ? 1 : 0;
        }
        return failed;
    }

    private static int failedUpdates(List<UpdateRegistration> registrations) {
        int failed = 0;
        for (UpdateRegistration registration : registrations) {
            failed += registration.getSnapshot().getState().name().equalsIgnoreCase("FAILED") ? 1 : 0;
        }
        return failed;
    }

    private Object journalSummary() {
        return Failures.runCatchingNonFatal(journal::get).getOrElse(error -> {
            Map<String, Object> failure = new LinkedHashMap<String, Object>();
            failure.put("error", error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
            return failure;
        });
    }

    private static List<Object> registrations(List<UpdateRegistration> registrations) {
        List<Object> details = new ArrayList<Object>();
        for (UpdateRegistration registration : registrations) {
            details.add(RuntimeUpdateDetails.registration(registration));
        }
        return details;
    }

    private Map<String, Object> library() {
        Map<String, Object> library = new LinkedHashMap<String, Object>();
        library.put("version", version);
        library.put("closed", closed.getAsBoolean());
        library.put("platform", identity.getId());
        library.put("implementation", identity.getImplementationName());
        library.put("proxy", identity.isProxy());
        library.put("server", identity.isServer());
        library.put("dataFolder", config.isPrivacy() ? "[redacted]" : dataFolder.toAbsolutePath().normalize().toString());
        library.put("configuration", RuntimeConfigurationDetails.of(config));
        return library;
    }

    private List<Object> configurationDeclarations() {
        List<Object> declarations = new ArrayList<Object>();
        for (org.pnfolder.pnlibrary.core.diagnostics.RegisteredDiagnosticConfiguration declaration : diagnostics.configurations("all")) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("plugin", declaration.getPlugin());
            entry.put("path", declaration.getConfiguration().getPath());
            entry.put("dataDirectory", config.isPrivacy() ? "[redacted]"
                    : declaration.getDataDirectory() == null ? null : declaration.getDataDirectory().toString());
            declarations.add(entry);
        }
        return declarations;
    }

    private Map<String, Object> updateGraph() {
        UpdatePlanSnapshot current = updates.getPlans().current().orElse(null);
        List<Object> recent = new ArrayList<Object>();
        for (UpdatePlanSnapshot snapshot : updates.getPlans().recent()) {
            recent.add(RuntimeUpdateDetails.of(snapshot));
        }
        Map<String, Object> graph = new LinkedHashMap<String, Object>();
        graph.put("current", current == null ? null : RuntimeUpdateDetails.of(current));
        graph.put("history", recent);
        return graph;
    }
}
