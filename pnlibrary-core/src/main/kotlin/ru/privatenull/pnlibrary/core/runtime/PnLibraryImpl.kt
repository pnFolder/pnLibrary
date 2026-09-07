package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.diagnostics.ReportGenerator
import ru.privatenull.pnlibrary.core.logging.PlatformLoggingService
import ru.privatenull.pnlibrary.core.logging.DiagnosticLogBuffer
import ru.privatenull.pnlibrary.core.metrics.MetricsRegistry
import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl
import ru.privatenull.pnlibrary.core.updates.UpdateServiceImpl
import ru.privatenull.pnlibrary.core.upload.EncryptedReportUploader
import ru.privatenull.pnlibrary.core.upload.MclogsUploader
import ru.privatenull.pnlibrary.core.upload.ReportUploader
import ru.privatenull.pnlibrary.core.upload.UploadLedger


import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.version.SemanticVersion
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
 * Concrete implementation of embedded [PnLibrary].
 */
class PnLibraryImpl(
    override val owner: Any,
    override val platform: PlatformAdapter,
    override val diagnostics: DiagnosticsRegistry,
    val config: PnLibraryConfig = PnLibraryConfig(),
    private val onClose: () -> Unit = {},
) : PnLibrary {

    override val version: String = platform.ownerDetails(owner)["version"] ?: "unknown"
    override fun isAtLeastVersion(minimumVersion: String): Boolean =
        SemanticVersion.tryParse(version)?.isAtLeast(minimumVersion) ?: false

    private val closedFlag = AtomicBoolean(false)
    private val reportInProgress = AtomicBoolean(false)
    override val isClosed: Boolean get() = closedFlag.get()
    private val metricsRegistry = MetricsRegistry(platform.metricsFactory)
    private val diagnosticLogs = DiagnosticLogBuffer(config.logRecords.coerceIn(10, 2_000))
    override val metrics: MetricsService get() = metricsRegistry
    override val logging: LoggingService = PlatformLoggingService(platform, diagnosticLogs)
    override val updates: ru.privatenull.pnlibrary.api.updates.UpdateService = UpdateServiceImpl(platform)
    override val tasks: ru.privatenull.pnlibrary.api.tasks.TaskService = TaskServiceImpl(platform) { taskOwner, message, error ->
        diagnosticLogs.record(platform, taskOwner, ru.privatenull.pnlibrary.api.logging.LogLevel.ERROR, message, error)
        platform.log(taskOwner, ru.privatenull.pnlibrary.api.logging.LogLevel.ERROR, message, error)
    }

    val dataFolder: Path = platform.dataFolder ?: extractDataFolder(owner)
    val uploadLedger: UploadLedger = UploadLedger(dataFolder.resolve("upload-ledger.json"))
    val encryptionCodec: EncryptedEnvelopeCodec? = initEncryptionCodec()
    val uploader: ReportUploader? = initUploader()
    val reportGenerator: ReportGenerator = ReportGenerator(
        dataFolder = dataFolder,
        config = config,
        diagnosticsRegistry = diagnostics,
        platformAdapter = platform,
        encryptionCodec = encryptionCodec,
        uploader = uploader,
        uploadLedger = uploadLedger,
        diagnosticLogs = diagnosticLogs::snapshot,
    )

    private val workerExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "pnLibrary-worker-${owner.javaClass.simpleName}").apply { isDaemon = true }
    }

    fun init() {
        val up = uploader
        if (up != null) {
            workerExecutor.scheduleWithFixedDelay({
                if (!isClosed) {
                    runCatching { uploadLedger.cleanup(up) }
                }
            }, 0, 24, TimeUnit.HOURS)
        }
    }

    fun generateReport(request: DebugRequest): ReportGenerator.ReportResult {
        check(!isClosed) { "pnLibrary instance is closed" }
        check(reportInProgress.compareAndSet(false, true)) { "A diagnostic report is already being generated" }
        return try {
            reportGenerator.generateAndSave(request)
        } finally {
            reportInProgress.set(false)
        }
    }

    override fun close() {
        if (closedFlag.compareAndSet(false, true)) {
            workerExecutor.shutdownNow()
            runCatching { metricsRegistry.close() }
            runCatching { (updates as AutoCloseable).close() }
            runCatching { tasks.close() }
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

    private fun initUploader(): ReportUploader? {
        if (!config.upload) return null
        return when (config.uploadMode) {
            "mclogs", "encrypted-mclogs" -> MclogsUploader()
            "encrypted" -> EncryptedReportUploader(
                endpoint = URI.create(config.uploadEndpoint),
                publicBase = URI.create(config.uploadPublicBase)
            )
            else -> null
        }
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
