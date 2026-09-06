package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.api.DebugRequest
import ru.privatenull.pnlibrary.api.PlatformAdapter
import ru.privatenull.pnlibrary.api.PnLibrary
import ru.privatenull.pnlibrary.api.PnLibraryConfig
import ru.privatenull.pnlibrary.api.MetricsService
import ru.privatenull.pnlibrary.api.PnLibraryProvider
import ru.privatenull.pnlibrary.api.LoggingService
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
    override fun isAtLeastVersion(minimumVersion: String): Boolean {
        fun parts(value: String) = value.removePrefix("v").substringBefore('-').split('.')
            .take(3).map { it.toIntOrNull() ?: 0 }.let { it + List(3 - it.size) { 0 } }
        val installed = parts(version)
        val required = parts(minimumVersion)
        return installed.zip(required).firstOrNull { it.first != it.second }
            ?.let { it.first > it.second } ?: true
    }

    private val closedFlag = AtomicBoolean(false)
    override val isClosed: Boolean get() = closedFlag.get()
    private val metricsRegistry = MetricsRegistry(platform.metricsFactory)
    override val metrics: MetricsService get() = metricsRegistry
    override val logging: LoggingService = PlatformLoggingService(platform)
    override val updates: ru.privatenull.pnlibrary.api.UpdateService = UpdateServiceImpl(platform)
    override val tasks: ru.privatenull.pnlibrary.api.TaskService = TaskServiceImpl(platform)

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
        return reportGenerator.generateAndSave(request)
    }

    override fun close() {
        if (closedFlag.compareAndSet(false, true)) {
            workerExecutor.shutdownNow()
            runCatching { metricsRegistry.close() }
            runCatching { (updates as AutoCloseable).close() }
            runCatching { tasks.close() }
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
        } catch (_: Exception) {
            null
        }
    }

    private fun initUploader(): ReportUploader? {
        if (!config.upload) return null
        return when (config.uploadMode) {
            "mclogs", "encrypted-mclogs" -> MclogsUploader()
            "encrypted" -> {
                try {
                    EncryptedReportUploader(
                        endpoint = URI.create(config.uploadEndpoint),
                        publicBase = URI.create(config.uploadPublicBase)
                    )
                } catch (_: Exception) {
                    null
                }
            }
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
