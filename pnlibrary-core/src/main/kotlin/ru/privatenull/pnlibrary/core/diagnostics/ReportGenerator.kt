package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import ru.privatenull.pnlibrary.core.upload.UploadProvider
import ru.privatenull.pnlibrary.core.upload.UploadLedger
import ru.privatenull.pnlibrary.core.upload.UploadReceipt
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Path
import java.time.Instant

/**
 * Coordinates collection, archive creation, encryption, local storage, and upload.
 *
 * Plaintext archives are always local-only. When `uploadMode` is `encrypted`, raw
 * registered configuration files and sensitive platform details may be included
 * because [EncryptedEnvelopeCodec] protects the complete ZIP before persistence
 * and upload. In plaintext mode, configurations pass through [ConfigReader].
 *
 * Report assembly is fail-fast for local collection and encryption errors. Upload
 * failures are instead returned in [DiagnosticReport.uploadError], preserving the
 * successfully written local report for manual support workflows.
 *
 * @param dataFolder runtime data root and parent of the `reports` directory
 * @param config immutable diagnostic and upload policy
 * @param diagnosticsRegistry source of plugin snapshots and configuration declarations
 * @param platformAdapter source of platform-specific diagnostic values
 * @param encryptionCodec envelope codec required in encrypted mode
 * @param uploader optional remote destination or ordered provider chain
 * @param uploadLedger optional persistence for remote deletion receipts
 * @param diagnosticLogs supplies recent bounded diagnostic incidents
 * @param diagnosticHistory supplies previously persisted incident snapshots
 */
internal class ReportGenerator(
    private val dataFolder: Path,
    private val config: PnLibraryConfig,
    private val diagnosticsRegistry: DiagnosticsRegistry,
    private val platformAdapter: PlatformAdapter,
    private val encryptionCodec: EncryptedEnvelopeCodec?,
    private val uploader: UploadProvider?,
    private val uploadLedger: UploadLedger?,
    private val diagnosticLogs: () -> List<Map<String, Any?>> = { emptyList() },
    private val diagnosticHistory: () -> List<Pair<String, ByteArray>> = { emptyList() },
) {

    private val systemCollector = SystemCollector()
    private val configReader = ConfigReader(dataFolder, config)
    private val reportStore = DiagnosticReportStore(dataFolder.resolve("reports"))

    /**
     * Generates one report according to [request], stores it locally, and optionally
     * uploads the encrypted artifact.
     *
     * @throws IllegalStateException when encrypted mode lacks an encryption codec
     * @throws IllegalArgumentException when the assembled archive exceeds the configured limit
     * @throws java.io.IOException when local collection or persistence fails
     */
    fun generateAndSave(request: DebugRequest): DiagnosticReport {
        val encryptionMode = config.uploadMode == "encrypted"
        val includeNetworkAddresses = encryptionMode

        val generated = Instant.now().toString()
        val archive = DiagnosticArchiveBuilder(config.maxReportBytes.toLong())
        val pluginDiagnostics = diagnosticsRegistry.snapshot(request.target)
        archive.json("manifest.json", linkedMapOf(
            "schemaVersion" to 3,
            "producer" to "pnLibrary",
            "generatedUtc" to generated,
            "target" to request.target,
            "platform" to platformAdapter.id,
            "platformImplementation" to platformAdapter.implementationName,
            "encrypted" to encryptionMode,
        ))
        archive.json("system.json", systemCollector.collect(includeNetworkAddresses = includeNetworkAddresses))
        archive.text("threads.txt", systemCollector.threadDump())
        archive.json("platform.json", platformAdapter.diagnosticDetails(includeSensitive = encryptionMode))

        pluginDiagnostics.forEach { (plugin, snapshot) ->
            archive.json("plugins/${safePath(plugin)}/diagnostics.json", snapshot)
        }

        if (request.logs && config.logs) {
            diagnosticLogs().takeLast(config.logRecords.coerceIn(1, 2_000))
                .groupBy { safePath(it["plugin"]?.toString() ?: "runtime") }
                .forEach { (plugin, logs) -> archive.json("plugins/$plugin/logs/incidents.json", logs) }
            diagnosticHistory().forEach { (name, content) ->
                archive.bytes("history/${safePath(name)}", content)
            }
        }

        if (request.configs && config.configs) {
            val declaredConfigs = diagnosticsRegistry.configurations(request.target)
            for (registered in declaredConfigs) {
                val root = registered.dataDirectory ?: dataFolder
                val pluginPath = "plugins/${safePath(registered.plugin)}/configuration/"
                if (encryptionMode) {
                    val collected = configReader.readExactFile(registered.configuration, root)
                    val path = safeConfigurationPath(collected.path)
                    if (collected.error == null) {
                        archive.bytes(pluginPath + path, requireNotNull(collected.content))
                    } else {
                        archive.text(pluginPath + "$path.error.txt", collected.error + "\n")
                    }
                } else {
                    val collected = configReader.readRedactedFile(registered.configuration, root)
                    val path = safeConfigurationPath(collected.path)
                    if (collected.error == null) archive.text(pluginPath + path, collected.content)
                    else archive.text(pluginPath + "$path.error.txt", collected.error + "\n")
                }
            }
        }
        val archiveBytes = archive.build()
        require(archiveBytes.size <= config.maxReportBytes) {
            "Diagnostic archive exceeds configured limit (${archiveBytes.size} > ${config.maxReportBytes} bytes)"
        }

        val encryptedPayload = if (encryptionMode) {
            val codec = encryptionCodec ?: throw IllegalStateException("Encryption is enabled but codec is null")
            codec.encryptBinary(archiveBytes, "zip")
        } else {
            null
        }

        val targetFile = reportStore.save(
            payload = encryptedPayload ?: archiveBytes,
            encrypted = encryptionMode,
            keepCount = config.keepReports,
        )

        // Upload if enabled and not local-only
        var uploadReceipt: UploadReceipt? = null
        var uploadError: String? = null
        if (!request.local && config.upload && uploader != null) {
            try {
                encryptedPayload ?: throw IllegalStateException(
                    "Binary plaintext archives are local-only; enable encrypted upload"
                )
                uploadReceipt = uploader.uploadFile(targetFile, "application/vnd.pnfolder.support")
                uploadLedger?.record(uploadReceipt, config.deleteAfterDays)
            } catch (error: Exception) {
                uploadError = error.message ?: error.javaClass.simpleName
            }
        }

        return DiagnosticReport(
            localFile = targetFile,
            encrypted = encryptionMode,
            uploadedUrl = uploadReceipt?.link?.toString(),
            uploadError = uploadError,
        )
    }

    private fun safePath(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifBlank { "unknown" }.take(96)

    private fun safeConfigurationPath(value: String): String = value.replace('\\', '/')
        .split('/').filter { it.isNotBlank() && it != "." && it != ".." }
        .joinToString("/") { component ->
            component.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(128).ifBlank { "config" }
        }.ifBlank { "config.txt" }

}
