package org.pnfolder.pnlibrary.core.observability

import org.pnfolder.pnlibrary.api.activity.ActivityCategory
import org.pnfolder.pnlibrary.api.activity.ActivitySeverity
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticReport
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.observability.ObservabilityReport
import org.pnfolder.pnlibrary.api.observability.ObservabilityReportRequest
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.core.logging.DiagnosticCapture
import org.pnfolder.pnlibrary.core.logging.DiagnosticLogBuffer
import org.pnfolder.pnlibrary.core.observability.history.IncidentHistoryStore
import org.pnfolder.pnlibrary.core.observability.report.SupportDeliveryFactory
import org.pnfolder.pnlibrary.core.observability.report.SupportReportGenerator
import org.pnfolder.pnlibrary.core.observability.reportSnapshot
import org.pnfolder.pnlibrary.core.upload.UploadLedger
import org.pnfolder.pnlibrary.spi.platform.PlatformDiagnostics
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformLogObservation
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Owns diagnostics, observation history, support reports, and their background work. */
internal class SupportRuntime(
    private val owner: Any,
    private val ownership: PlatformOwnership,
    private val identity: PlatformIdentity,
    private val platformDiagnostics: PlatformDiagnostics,
    private val platformLogObservation: PlatformLogObservation,
    private val platformLogging: PlatformLogging,
    private val diagnostics: DiagnosticsRegistry,
    private val config: PnLibraryConfig,
    dataFolder: Path,
    private val runtimeDiagnostics: () -> Map<String, Any?> = { emptyMap() },
    private val journal: ((Any, LogLevel, String, Throwable?) -> Unit)? = null,
    private val onRuntimeError: (Any, Throwable) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val observations = ObservabilityRuntime(dataFolder)
    private val diagnosticBridge = DiagnosticObservationBridge(observations)
    val service = ObservabilityServiceImpl(diagnostics, observations)
    val logs = DiagnosticLogBuffer(config.logRecords.coerceIn(10, 2_000))
    val capture = DiagnosticCapture(ownership, platformLogging, logs, journal) { logOwner, message, error ->
        service.record(
            type = "RUNTIME_ERROR",
            category = ActivityCategory.ERROR,
            severity = ActivitySeverity.ERROR,
            source = logOwner.javaClass.name,
            metadata = mapOf("message" to message.take(512), "exception" to error.javaClass.name),
        )
        onRuntimeError(logOwner, error)
    }

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
        platformIdentity = identity,
        platformDiagnostics = platformDiagnostics,
        encryptionCodec = encryption,
        uploader = uploader,
        uploadLedger = uploadLedger,
        diagnosticLogs = logs::snapshot,
        diagnosticHistory = history::files,
        observabilitySnapshot = observations::reportSnapshot,
        runtimeDiagnostics = runtimeDiagnostics,
    )
    private val persistRequested = AtomicBoolean(false)
    private val reportInProgress = AtomicBoolean(false)
    private val reportsCreated = AtomicLong()
    private val reportsFailed = AtomicLong()
    private val reportDurationTotalMs = AtomicLong()
    private val reportSizeTotalBytes = AtomicLong()
    private val reportFailuresByType = ConcurrentHashMap<String, AtomicLong>()
    @Volatile private var lastReportUtc: String? = null
    @Volatile private var lastReportDurationMs: Long? = null
    @Volatile private var lastReportSizeBytes: Long? = null
    private val worker = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "pnLibrary-support-${owner.javaClass.simpleName}").apply { isDaemon = true }
    }

    fun initialize(isRuntimeClosed: () -> Boolean) {
        observations.configureReportFactory(::createObservabilityReport)
        logs.onChange { persistHistory() }
        diagnostics.onEventsChanged(::persistHistory)
        diagnostics.onActivityEvent(diagnosticBridge::record)
        platformLogObservation.observeNativeLogs { nativeOwner, level, message, error ->
            capture.observe(nativeOwner, level, message, error, config.journalForeignPlugins || nativeOwner === owner)
        }

        worker.scheduleWithFixedDelay(
            { if (!isRuntimeClosed()) runCatchingNonFatal { performMaintenance() }.onFailure(warnBackgroundFailure("Diagnostic retention maintenance")) },
            MAINTENANCE_INTERVAL_HOURS,
            MAINTENANCE_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )

        uploader?.let { remote ->
            worker.scheduleWithFixedDelay(
                { if (!isRuntimeClosed()) runCatchingNonFatal { uploadLedger.cleanup(remote) }.onFailure(warnBackgroundFailure("Uploaded report cleanup")) },
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
        val startedAt = System.nanoTime()
        return try {
            val report = reports.generateAndSave(request)
            reportsCreated.incrementAndGet()
            lastReportUtc = Instant.now().toString()
            lastReportDurationMs = (System.nanoTime() - startedAt) / 1_000_000
            lastReportSizeBytes = runCatchingNonFatal { Files.size(report.localFile) }.getOrNull()
            reportDurationTotalMs.addAndGet(lastReportDurationMs ?: 0L)
            reportSizeTotalBytes.addAndGet(lastReportSizeBytes ?: 0L)
            report
        } catch (error: Throwable) {
            reportsFailed.incrementAndGet()
            reportFailuresByType.computeIfAbsent(error.javaClass.name) { AtomicLong() }.incrementAndGet()
            lastReportDurationMs = (System.nanoTime() - startedAt) / 1_000_000
            throw error
        } finally {
            reportInProgress.set(false)
        }
    }

    fun reportSummary(): Map<String, Any?> {
        val historyFiles = runCatchingNonFatal { history.files() }.getOrDefault(emptyList())
        return linkedMapOf(
            "inProgress" to reportInProgress.get(),
            "created" to reportsCreated.get(),
            "failed" to reportsFailed.get(),
            "total" to reportsCreated.get() + reportsFailed.get(),
            "successRate" to reportsCreated.get().toDouble() /
                (reportsCreated.get() + reportsFailed.get()).coerceAtLeast(1).toDouble(),
            "averageDurationMs" to reportDurationTotalMs.get().toDouble() /
                reportsCreated.get().coerceAtLeast(1).toDouble(),
            "averageSizeBytes" to reportSizeTotalBytes.get().toDouble() /
                reportsCreated.get().coerceAtLeast(1).toDouble(),
            "failuresByType" to reportFailuresByType
                .entries
                .sortedByDescending { it.value.get() }
                .associate { it.key to it.value.get() },
            "lastCreatedUtc" to lastReportUtc,
            "lastDurationMs" to lastReportDurationMs,
            "lastSizeBytes" to lastReportSizeBytes,
            "maxReportBytes" to config.maxReportBytes,
            "lastSizeRatio" to lastReportSizeBytes?.let {
                it.toDouble() / config.maxReportBytes.toDouble().coerceAtLeast(1.0)
            },
        "history" to linkedMapOf<String, Any?>(
                "fileCount" to historyFiles.size,
                "totalBytes" to historyFiles.sumOf { it.second.size.toLong() },
                "files" to historyFiles.map { it.first },
            ),
            "uploadLedger" to uploadLedger.diagnosticSnapshot(),
        )
    }

    fun observabilityAnalytics(): Map<String, Any?> = observations.analytics()

    fun recordRuntimeError(logOwner: Any, message: String, error: Throwable) {
        capture.log(logOwner, LogLevel.ERROR, message, error)
    }

    override fun close() {
        platformLogObservation.observeNativeLogs(null)
        diagnostics.onEventsChanged(null)
        diagnostics.onActivityEvent(null)
        worker.shutdownNow()
        if (persistRequested.getAndSet(false)) runCatchingNonFatal { writeHistory() }
        diagnostics.clear()
        service.close()
    }

    /**
     * Requests a history snapshot. Bursts of incidents share one write, so a logging thread never
     * waits for encryption and disk I/O; [close] flushes anything still pending.
     */
    private fun persistHistory() {
        if (!persistRequested.compareAndSet(false, true)) return
        try {
            worker.schedule(
                { if (persistRequested.getAndSet(false)) runCatchingNonFatal { writeHistory() } },
                HISTORY_FLUSH_DELAY_MS,
                TimeUnit.MILLISECONDS,
            )
        } catch (_: RejectedExecutionException) {
            persistRequested.set(false)
        }
    }

    /** Logs a failed background job; a periodic task must not throw, or the executor cancels it. */
    private fun warnBackgroundFailure(job: String): (Throwable) -> Unit = { error ->
        capture.log(this, LogLevel.WARNING, "$job failed", error)
    }

    // History writes stay silent: logging their failure would record a new incident and request another write.
    private fun writeHistory() {
        history.save(logs.snapshot(), diagnostics.eventSnapshot())
    }

    /** Applies every retention period: in-memory incidents, observations, attachments and history files. */
    internal fun performMaintenance() {
        logs.expireOlderThan(System.currentTimeMillis() - config.historyRetentionDays * MILLIS_PER_DAY)
        observations.maintain()
        history.prune()
    }

    private companion object {
        const val HISTORY_FLUSH_DELAY_MS = 2_000L
        const val MAINTENANCE_INTERVAL_HOURS = 1L
        const val MILLIS_PER_DAY = 86_400_000L
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
