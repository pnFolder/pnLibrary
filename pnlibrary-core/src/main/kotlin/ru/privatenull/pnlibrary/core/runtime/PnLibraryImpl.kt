package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.config.ConfigurationServiceImpl
import ru.privatenull.pnlibrary.core.diagnostics.PersistentDiagnosticHistory
import ru.privatenull.pnlibrary.core.diagnostics.ReportGenerator
import ru.privatenull.pnlibrary.core.events.EventServiceImpl
import ru.privatenull.pnlibrary.core.logging.DiagnosticLogBuffer
import ru.privatenull.pnlibrary.core.logging.PlatformLoggingService
import ru.privatenull.pnlibrary.core.metrics.MetricsRegistry
import ru.privatenull.pnlibrary.core.plugin.PluginRegistryImpl
import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl
import ru.privatenull.pnlibrary.core.updates.UpdateServiceImpl
import ru.privatenull.pnlibrary.core.upload.EncryptedReportUploader
import ru.privatenull.pnlibrary.core.upload.CatboxUploader
import ru.privatenull.pnlibrary.core.upload.FileIoUploader
import ru.privatenull.pnlibrary.core.upload.UploadProvider
import ru.privatenull.pnlibrary.core.upload.UploadProviderChain
import ru.privatenull.pnlibrary.core.upload.UploadLedger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Composes all service implementations into one [PnLibrary] runtime.
 *
 * This is the internal composition root for diagnostics, events, logging,
 * metrics, updates, and tasks. Platform modules depend on [PnLibrary] and must
 * not cast the facade to this implementation.
 */
class PnLibraryImpl(
    override val owner: Any,
    private val platform: PlatformAdapter,
    override val diagnostics: DiagnosticsRegistry,
    val config: PnLibraryConfig = PnLibraryConfig(),
    private val onClose: () -> Unit = {},
) : PnLibrary {

    override val configuration: PnLibraryConfig get() = config

    override val version: String = platform.ownerDetails(owner)["version"] ?: "unknown"
    override fun isAtLeastVersion(minimumVersion: String): Boolean =
        SemanticVersion.tryParse(version)?.isAtLeast(minimumVersion) ?: false

    private val closedFlag = AtomicBoolean(false)
    private val reportInProgress = AtomicBoolean(false)
    override val isClosed: Boolean get() = closedFlag.get()
    private val metricsRegistry = MetricsRegistry(platform.metricsFactory)
    val dataFolder: Path = platform.dataFolder ?: extractDataFolder(owner)
    val uploadLedger: UploadLedger = UploadLedger(dataFolder.resolve("upload-ledger.json"))
    val encryptionCodec: EncryptedEnvelopeCodec? = initEncryptionCodec()
    private val diagnosticHistory = PersistentDiagnosticHistory(
        dataFolder.resolve("diagnostics").resolve("history"),
        encryptionCodec,
        config.historyRetentionDays,
        config.historyMaxBytes.toLong(),
    )
    private val diagnosticLogs = DiagnosticLogBuffer(config.logRecords.coerceIn(10, 2_000))
    override val metrics: MetricsService get() = metricsRegistry
    override val logging: LoggingService = PlatformLoggingService(platform, diagnosticLogs)
    private val configurationService = ConfigurationServiceImpl(platform)
    override val configurations: ru.privatenull.pnlibrary.api.config.ConfigurationService get() = configurationService
    private val updateService = UpdateServiceImpl(platform)
    override val updates: ru.privatenull.pnlibrary.api.updates.UpdateService get() = updateService
    private val taskService = TaskServiceImpl(platform) { taskOwner, message, error ->
        recordAndLog(taskOwner, message, error)
    }
    override val tasks: ru.privatenull.pnlibrary.api.tasks.TaskService get() = taskService
    private val serviceManager = ServiceManagerImpl()
    override val services: ru.privatenull.pnlibrary.api.services.ServiceManager get() = serviceManager
    private val eventService = EventServiceImpl(taskService) { pluginId, message, error ->
        val identifiedMessage = "[$pluginId] $message"
        recordAndLog(owner, identifiedMessage, error)
    }
    override val events: ru.privatenull.pnlibrary.api.events.EventService get() = eventService
    override val plugins: ru.privatenull.pnlibrary.api.plugin.PluginRegistry = PluginRegistryImpl(
        platform = platform,
        events = eventService,
        tasks = taskService,
        services = serviceManager,
        logging = logging,
        configurations = configurationService,
        metrics = metricsRegistry,
        diagnostics = diagnostics,
        updates = updateService,
    )

    val uploader: UploadProvider? = initUploader()
    val reportGenerator: ReportGenerator = ReportGenerator(
        dataFolder = dataFolder,
        config = config,
        diagnosticsRegistry = diagnostics,
        platformAdapter = platform,
        encryptionCodec = encryptionCodec,
        uploader = uploader,
        uploadLedger = uploadLedger,
        diagnosticLogs = diagnosticLogs::snapshot,
        diagnosticHistory = diagnosticHistory::files,
    )

    private val workerExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "pnLibrary-worker-${owner.javaClass.simpleName}").apply { isDaemon = true }
    }

    fun init() {
        diagnosticLogs.onChange { persistDiagnosticHistory() }
        diagnostics.onEventsChanged(::persistDiagnosticHistory)
        platform.observeNativeLogs { nativeOwner, level, message, error ->
            diagnosticLogs.record(platform, nativeOwner, level, message, error)
        }
        val up = uploader
        if (up != null) {
            workerExecutor.scheduleWithFixedDelay({
                if (!isClosed) {
                    runCatching { uploadLedger.cleanup(up) }
                }
            }, 0, 24, TimeUnit.HOURS)
        }
    }

    private fun persistDiagnosticHistory() {
        diagnosticHistory.save(diagnosticLogs.snapshot(), diagnostics.eventSnapshot())
    }

    override fun createDiagnosticReport(request: DebugRequest): DiagnosticReport {
        check(!isClosed) { "pnLibrary instance is closed" }
        check(reportInProgress.compareAndSet(false, true)) { "A diagnostic report is already being generated" }
        return try {
            reportGenerator.generateAndSave(request)
        } finally {
            reportInProgress.set(false)
        }
    }

    private fun recordAndLog(logOwner: Any, message: String, error: Throwable) {
        val level = ru.privatenull.pnlibrary.api.logging.LogLevel.ERROR
        val capture = diagnosticLogs.record(platform, logOwner, level, message, error)
        when {
            capture == null || capture.emitOriginal -> platform.log(logOwner, level, message, error)
            capture.summary != null -> platform.log(logOwner, level, capture.summary, null)
        }
    }

    override fun close() {
        if (closedFlag.compareAndSet(false, true)) {
            workerExecutor.shutdownNow()
            runCatching { plugins.close() }
            runCatching { configurationService.close() }
            runCatching { metricsRegistry.close() }
            runCatching { updateService.close() }
            runCatching { eventService.close() }
            runCatching { serviceManager.close() }
            runCatching { taskService.close() }
            runCatching { platform.observeNativeLogs(null) }
            diagnostics.onEventsChanged(null)
            diagnostics.clear()
            PnLibraryProvider.clear(this)
            runCatching { platform.close() }
            onClose()
        }
    }

    private fun initEncryptionCodec(): EncryptedEnvelopeCodec? {
        if (!config.uploadMode.startsWith("encrypted")) return null
        return try {
            val keyPem = resolvePublicKey(config)
            EncryptedEnvelopeCodec(keyPem, config.uploadKeyId)
        } catch (error: Exception) {
            throw IllegalStateException("Unable to initialize diagnostic encryption", error)
        }
    }

    private fun initUploader(): UploadProvider? {
        if (!config.upload) return null
        if (config.uploadMode == "disabled") return null
        val providers = config.uploadProviders.map { id -> when (id) {
            "catbox" -> CatboxUploader()
            "fileio" -> FileIoUploader()
            "custom" -> EncryptedReportUploader(
                endpoint = URI.create(config.uploadEndpoint), publicBase = URI.create(config.uploadPublicBase)
            )
            else -> error("Unsupported upload provider: $id")
        } }
        return UploadProviderChain(providers)
    }

    private fun resolvePublicKey(cfg: PnLibraryConfig): String {
        if (cfg.uploadPublicKey.isNotBlank()) return cfg.uploadPublicKey
        val stream: InputStream = PnLibraryImpl::class.java.getResourceAsStream("/diagnostic-public.pem")
            ?: throw IllegalArgumentException("Bundled diagnostic public key missing")
        return stream.use { input ->
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(2048)
            var len: Int
            while (input.read(buffer).also { len = it } != -1) {
                baos.write(buffer, 0, len)
            }
            String(baos.toByteArray(), StandardCharsets.US_ASCII)
        }
    }

    private fun extractDataFolder(owner: Any): Path {
        return try {
            val method = owner.javaClass.getMethod("getDataFolder")
            val file = method.invoke(owner) as java.io.File
            file.toPath()
        } catch (_: Exception) {
            Paths.get("plugins", owner.javaClass.simpleName)
        }
    }
}
