package org.pnfolder.pnlibrary.core.observability;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationRequest;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReport;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReportRequest;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.core.logging.DiagnosticCapture;
import org.pnfolder.pnlibrary.core.logging.DiagnosticLogBuffer;
import org.pnfolder.pnlibrary.core.observability.history.IncidentHistoryStore;
import org.pnfolder.pnlibrary.core.observability.report.EncryptedEnvelopeCodec;
import org.pnfolder.pnlibrary.core.observability.report.ObservabilityReportSnapshot;
import org.pnfolder.pnlibrary.core.observability.report.SupportDeliveryFactory;
import org.pnfolder.pnlibrary.core.observability.report.SupportReportGenerator;
import org.pnfolder.pnlibrary.core.upload.UploadLedger;
import org.pnfolder.pnlibrary.core.upload.UploadProvider;
import org.pnfolder.pnlibrary.spi.platform.diagnostics.PlatformDiagnostics;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogObservation;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Owns diagnostics, observation history, support reports, and their background work. */
public final class SupportRuntime implements AutoCloseable {
    private static final long HISTORY_FLUSH_DELAY_MS = 2_000L;
    private static final long MAINTENANCE_INTERVAL_HOURS = 1L;
    private static final long MILLIS_PER_DAY = 86_400_000L;

    private final Object owner;
    private final PlatformLogObservation platformLogObservation;
    private final DiagnosticsRegistry diagnostics;
    private final PnLibraryConfig config;
    private final BiConsumer<Object, Throwable> onRuntimeError;
    private final ObservabilityRuntime observations;
    private final ObservabilityHub service;
    private final DiagnosticLogBuffer logs;
    private final DiagnosticCapture capture;
    private final UploadLedger uploadLedger;
    private final IncidentHistoryStore history;
    private final UploadProvider uploader;
    private final SupportReportGenerator reports;
    private final SupportReportMetrics reportMetrics = new SupportReportMetrics();
    private final AtomicBoolean persistRequested = new AtomicBoolean(false);
    private final FailureThrottle internalFailures = new FailureThrottle();
    private final ScheduledExecutorService worker;

    /**
     * Creates the runtime and its stores under {@code paths}.
     *
     * @param captureFactory builds the log capture around the incident buffer; its errors go to the sink it is given
     * @param runtimeDiagnostics supplies the runtime section of support reports
     * @param onRuntimeError told about errors the library met while handling log lines
     */
    public SupportRuntime(@NotNull Object owner, @NotNull PlatformIdentity identity,
                          @NotNull PlatformDiagnostics platformDiagnostics,
                          @NotNull PlatformLogObservation platformLogObservation,
                          @NotNull CaptureFactory captureFactory, @NotNull DiagnosticsRegistry diagnostics,
                          @NotNull PnLibraryConfig config, @NotNull LibraryPaths paths,
                          @NotNull Supplier<Map<String, Object>> runtimeDiagnostics,
                          @NotNull BiConsumer<Object, Throwable> onRuntimeError) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.platformLogObservation = Objects.requireNonNull(platformLogObservation, "platformLogObservation");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.config = Objects.requireNonNull(config, "config");
        this.onRuntimeError = Objects.requireNonNull(onRuntimeError, "onRuntimeError");
        this.observations = new ObservabilityRuntime(paths);
        this.service = new ObservabilityHub(diagnostics, observations);
        this.logs = new DiagnosticLogBuffer(Math.max(10, Math.min(config.getLogRecords(), 2_000)));
        this.capture = captureFactory.create(logs, this::runtimeError);
        SupportDeliveryFactory delivery = new SupportDeliveryFactory(config);
        this.uploadLedger = new UploadLedger(paths.getUploadLedger());
        EncryptedEnvelopeCodec encryption = delivery.encryptionCodec();
        this.history = new IncidentHistoryStore(paths.getDiagnosticsHistory(), encryption,
                config.getHistoryRetentionDays(), config.getHistoryMaxBytes());
        this.uploader = delivery.uploader();
        this.reports = new SupportReportGenerator(paths.getPluginFolder(), paths.getReports(), config, diagnostics,
                identity, platformDiagnostics, encryption, uploader, uploadLedger, logs::snapshot, this::historyFiles,
                () -> ObservabilityReportSnapshot.of(observations), runtimeDiagnostics);
        this.worker = Executors.newSingleThreadScheduledExecutor(action -> {
            Thread thread = new Thread(action, "pnLibrary-support-" + owner.getClass().getSimpleName());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The diagnostics and observability service that plugins see. */
    @NotNull
    public ObservabilityHub getService() {
        return service;
    }

    /** The buffer of recent warnings and errors. */
    @NotNull
    public DiagnosticLogBuffer getLogs() {
        return logs;
    }

    /** The log capture that platform logging goes through. */
    @NotNull
    public DiagnosticCapture getCapture() {
        return capture;
    }

    /** Counts for the diagnostics report. */
    @NotNull
    public Map<String, Object> logSummary() {
        return logs.summary();
    }

    /** Hooks the runtime into diagnostics and native logs and starts the background jobs. */
    public void initialize(@NotNull BooleanSupplier isRuntimeClosed) {
        diagnostics.onInternalFailure(this::reportInternalFailure);
        observations.configureReportFactory(this::createObservabilityReport);
        logs.onChange(incidents -> persistHistory());
        diagnostics.onEventsChanged(this::persistHistory);
        diagnostics.onActivityEvent(this::recordDiagnostic);
        platformLogObservation.observeNativeLogs((nativeOwner, level, message, error) -> {
            if (nativeOwner == owner) {
                capture.observe(nativeOwner, level, message, error);
            } else {
                capture.observeWithoutJournal(nativeOwner, level, message, error);
            }
        });
        schedule(isRuntimeClosed, this::performMaintenance, MAINTENANCE_INTERVAL_HOURS, MAINTENANCE_INTERVAL_HOURS, "Diagnostic retention maintenance");
        if (uploader != null) {
            schedule(isRuntimeClosed, () -> uploadLedger.cleanup(uploader), 0, 24, "Uploaded report cleanup");
        }
    }

    /**
     * Generates a support report; only one runs at a time.
     *
     * @throws IllegalStateException when another report is being generated
     * @throws IOException when the report cannot be collected or stored
     */
    @NotNull
    public DiagnosticReport createDiagnosticReport(@NotNull DebugRequest request) throws IOException {
        if (!reportMetrics.tryStart()) {
            throw new IllegalStateException("A diagnostic report is already being generated");
        }
        long startedAt = System.nanoTime();
        try {
            DiagnosticReport report = reports.generateAndSave(request);
            reportMetrics.succeeded(startedAt, report.getLocalFile());
            return report;
        } catch (IOException | RuntimeException | Error error) {
            reportMetrics.failed(startedAt, error);
            throw error;
        } finally {
            reportMetrics.finish();
        }
    }

    /** Report counts, persisted history and the upload ledger for the diagnostics report. */
    @NotNull
    public Map<String, Object> reportSummary() {
        List<Map.Entry<String, byte[]>> historyFiles = Failures.runCatchingNonFatal(history::files)
                .getOrDefault(Collections.emptyList());
        return reportMetrics.summary(historyFiles, uploadLedger.diagnosticSnapshot(), config.getMaxReportBytes());
    }

    /** Counts and rates over the recorded observations. */
    @NotNull
    public Map<String, Object> observabilityAnalytics() {
        return observations.analytics();
    }

    /** Writes an error of the library itself to the log with the incident capture. */
    public void recordRuntimeError(@NotNull Object logOwner, @NotNull String message, @NotNull Throwable error) {
        capture.log(logOwner, LogLevel.ERROR, message, error);
    }

    @Override
    public void close() {
        platformLogObservation.observeNativeLogs(null);
        diagnostics.onEventsChanged(null);
        diagnostics.onActivityEvent(null);
        worker.shutdownNow();
        if (persistRequested.getAndSet(false)) {
            saveHistory();
        }
        diagnostics.clear();
        service.close();
    }

    /** Applies every retention period: in-memory incidents, observations, attachments and history files. */
    public void performMaintenance() throws IOException {
        logs.expireOlderThan(System.currentTimeMillis() - config.getHistoryRetentionDays() * MILLIS_PER_DAY);
        observations.maintain();
        history.prune();
    }

    private List<Map.Entry<String, byte[]>> historyFiles() {
        try {
            return history.files();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private interface Job {
        void run() throws Exception;
    }

    private void schedule(BooleanSupplier isRuntimeClosed, Job job, long initialDelayHours, long periodHours,
                          String description) {
        worker.scheduleWithFixedDelay(() -> {
            if (isRuntimeClosed.getAsBoolean()) {
                return;
            }
            Failures.runCatchingNonFatal(() -> {
                job.run();
                return null;
            }).onFailure(error -> capture.log(this, LogLevel.WARNING, description + " failed", error));
        }, initialDelayHours, periodHours, TimeUnit.HOURS);
    }

    private void runtimeError(Object logOwner, String message, Throwable error) {
        service.record(new ObservationRequest(null, logOwner.getClass().getName(), "RUNTIME_ERROR",
                ObservationLevel.ERROR, runtimeErrorData(message, error), Collections.emptyList(), null));
        onRuntimeError.accept(logOwner, error);
    }

    private static Map<String, String> runtimeErrorData(String message, Throwable error) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("message", message.length() > 512 ? message.substring(0, 512) : message);
        data.put("exception", error.getClass().getName());
        return data;
    }

    /**
     * Requests a history snapshot. Bursts of incidents share one write, so a logging thread never waits for
     * encryption and disk I/O; {@link #close()} flushes anything still pending.
     */
    private void persistHistory() {
        if (!persistRequested.compareAndSet(false, true)) {
            return;
        }
        try {
            worker.schedule(() -> {
                if (persistRequested.getAndSet(false)) {
                    saveHistory();
                }
            }, HISTORY_FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException closed) {
            persistRequested.set(false);
        }
    }

    /** Turns one diagnostic incident into one observation. */
    private void recordDiagnostic(String plugin, DiagnosticLevel level, String component, String code, String message,
                                  Throwable error, Map<String, Object> fields) {
        Map<String, String> data = new LinkedHashMap<>(ObservationConversions.toStringValues(fields));
        data.put("code", code);
        data.put("hasException", Boolean.toString(error != null));
        observations.record(new ObservationRequest(plugin, component, message,
                ObservationConversions.toObservationLevel(level), data,
                Collections.emptyList(), error));
    }

    /** Console only: recording it would raise another diagnostic event and run the failing callback again. */
    private void reportInternalFailure(String what, Throwable error) {
        if (!internalFailures.shouldReport(what + "|" + error.getClass().getName())) {
            return;
        }
        capture.writeUnrecorded(this, LogLevel.WARNING, what, error);
    }

    /** A failed write is shown on the console only: recording it would add an incident and request another write. */
    private void saveHistory() {
        Failures.runCatchingNonFatal(() -> {
            history.save(logs.snapshot(), diagnostics.eventSnapshot());
            return null;
        }).onFailure(error -> reportInternalFailure("Diagnostic history could not be saved", error));
    }

    private ObservabilityReport createObservabilityReport(ObservabilityReportRequest request) {
        try {
            DiagnosticReport report = reports.generateAndSave(
                    new DebugRequest(request.getTarget(), request.isIncludeConfigurations(), request.isIncludeLogs(), true));
            return new ObservabilityReport(report.getLocalFile().getFileName().toString(), report.getLocalFile(),
                    Files.getLastModifiedTime(report.getLocalFile()).toMillis());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
