package ru.privatenull.pnlibrary.core.observability

import ru.privatenull.pnlibrary.api.activity.ActivityCategory
import ru.privatenull.pnlibrary.api.activity.ActivitySeverity
import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.observability.ObservabilityReport
import ru.privatenull.pnlibrary.api.observability.ObservabilityReportRequest
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.logging.DiagnosticLogBuffer
import ru.privatenull.pnlibrary.core.observability.history.IncidentHistoryStore
import ru.privatenull.pnlibrary.core.observability.report.SupportDeliveryFactory
import ru.privatenull.pnlibrary.core.observability.report.SupportReportGenerator
import ru.privatenull.pnlibrary.core.observability.reportSnapshot
import ru.privatenull.pnlibrary.core.upload.UploadLedger
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Owns diagnostics, observation history, support reports, and their background work. */
internal class SupportRuntime(
    private val owner: Any,
    private val platform: PlatformAdapter,
    private val diagnostics: DiagnosticsRegistry,
    private val config: PnLibraryConfig,
    dataFolder: Path,
    private val runtimeDiagnostics: () -> Map<String, Any?> = { emptyMap() },
) : AutoCloseable {
    private val observations = ObservabilityRuntime(dataFolder)
    private val diagnosticBridge = DiagnosticObservationBridge(observations)
    val service = UnifiedObservabilityService(diagnostics, observations)
    val logs = DiagnosticLogBuffer(config.logRecords.coerceIn(10, 2_000))

    fun logSummary(): Map<String, Any?> = logs.summary()

    private val delivery = SupportDeliveryFactory(config)
    private val uploadLedger = UploadLedger(dataFolder.resolve("upload-ledger.json"))
    private val encryption = delivery.encryptionCodec()
    private val history = IncidentHistoryStore(
        directory = dataFolder.resolve("diagnostics/history"),
        codec = encryption,
        retentionDays = config.historyRetentionDays,
        maxBytes = config.historyMaxBytes.toLong(),
    )
    private val uploader = delivery.uploader()
    private val reports = SupportReportGenerator(
        dataFolder = dataFolder,
        config = config,
        diagnosticsRegistry = diagnostics,
        platformAdapter = platform,
        encryptionCodec = encryption,
        uploader = uploader,
        uploadLedger = uploadLedger,
        diagnosticLogs = logs::snapshot,
        diagnosticHistory = history::files,
        observabilitySnapshot = observations::reportSnapshot,
        runtimeDiagnostics = runtimeDiagnostics,
    )
    private val reportInProgress = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "pnLibrary-support-${owner.javaClass.simpleName}").apply { isDaemon = true }
    }

    fun initialize(isRuntimeClosed: () -> Boolean) {
        observations.configureReportFactory(::createObservabilityReport)
        logs.onChange { persistHistory() }
        diagnostics.onEventsChanged(::persistHistory)
        diagnostics.onActivityEvent(diagnosticBridge::record)
        platform.observeNativeLogs { nativeOwner, level, message, error ->
            logs.record(platform, nativeOwner, level, message, error)
        }

        uploader?.let { remote ->
            worker.scheduleWithFixedDelay(
                { if (!isRuntimeClosed()) runCatching { uploadLedger.cleanup(remote) } },
                0,
                24,
                TimeUnit.HOURS,
            )
        }
    }

    fun createDiagnosticReport(request: DebugRequest): DiagnosticReport {
        check(reportInProgress.compareAndSet(false, true)) {
            "A diagnostic report is already being generated"
        }
        return try {
            reports.generateAndSave(request)
        } finally {
            reportInProgress.set(false)
        }
    }

    fun recordRuntimeError(logOwner: Any, message: String, error: Throwable) {
        service.record(
            type = "RUNTIME_ERROR",
            category = ActivityCategory.ERROR,
            severity = ActivitySeverity.ERROR,
            source = logOwner.javaClass.name,
            metadata = mapOf(
                "message" to message.take(512),
                "exception" to error.javaClass.name,
            ),
        )

        val capture = logs.record(platform, logOwner, LogLevel.ERROR, message, error)
        when {
            capture == null || capture.emitOriginal -> platform.log(logOwner, LogLevel.ERROR, message, error)
            capture.summary != null -> platform.log(logOwner, LogLevel.ERROR, capture.summary, null)
        }
    }

    override fun close() {
        worker.shutdownNow()
        platform.observeNativeLogs(null)
        diagnostics.onEventsChanged(null)
        diagnostics.onActivityEvent(null)
        diagnostics.clear()
        service.close()
    }

    private fun persistHistory() {
        history.save(logs.snapshot(), diagnostics.eventSnapshot())
    }

    private fun createObservabilityReport(request: ObservabilityReportRequest): ObservabilityReport {
        val report = reports.generateAndSave(
            DebugRequest(
                target = request.target,
                logs = request.includeLogs,
                configs = request.includeConfigurations,
                local = true,
            ),
        )
        return ObservabilityReport(
            id = report.localFile.fileName.toString(),
            file = report.localFile,
            createdAt = Files.getLastModifiedTime(report.localFile).toMillis(),
        )
    }
}
