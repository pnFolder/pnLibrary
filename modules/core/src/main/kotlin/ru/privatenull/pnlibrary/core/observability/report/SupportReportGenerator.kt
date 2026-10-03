package ru.privatenull.pnlibrary.core.observability.report

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.core.diagnostics.ConfigReader
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.diagnostics.SystemCollector
import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import ru.privatenull.pnlibrary.core.upload.UploadProvider
import ru.privatenull.pnlibrary.core.upload.UploadLedger
import ru.privatenull.pnlibrary.core.upload.UploadReceipt
import ru.privatenull.pnlibrary.core.observability.ObservabilityReportSnapshot
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
internal class SupportReportGenerator(
    private val dataFolder: Path,
    private val config: PnLibraryConfig,
    private val diagnosticsRegistry: DiagnosticsRegistry,
    private val platformAdapter: PlatformAdapter,
    private val encryptionCodec: EncryptedEnvelopeCodec?,
    private val uploader: UploadProvider?,
    private val uploadLedger: UploadLedger?,
    private val diagnosticLogs: () -> List<Map<String, Any?>> = { emptyList() },
    private val diagnosticHistory: () -> List<Pair<String, ByteArray>> = { emptyList() },
    private val observabilitySnapshot: () -> ObservabilityReportSnapshot = {
        ObservabilityReportSnapshot(ByteArray(0), ByteArray(0), emptyMap())
    },
) {

    private val systemCollector = SystemCollector()
    private val configReader = ConfigReader(dataFolder, config)
    private val reportStore = SupportReportStore(dataFolder.resolve("reports"))

    /**
     * Generates one report according to [request], stores it locally, and optionally
     * uploads the encrypted artifact.
     *
     * @throws IllegalStateException when encrypted mode lacks an encryption codec
     * @throws IllegalArgumentException when the assembled archive exceeds the configured limit
     * @throws java.io.IOException when local collection or persistence fails
     */
    fun generateAndSave(request: DebugRequest): DiagnosticReport {
        val encrypted = config.uploadMode == "encrypted"
        val archiveBytes = buildArchive(request, encrypted)
        val encryptedPayload = encrypt(archiveBytes, encrypted)
        val reportFile = reportStore.save(
            payload = encryptedPayload ?: archiveBytes,
            encrypted = encrypted,
            keepCount = config.keepReports,
        )
        val upload = upload(reportFile, encryptedPayload, request)

        return DiagnosticReport(
            localFile = reportFile,
            encrypted = encrypted,
            uploadedUrl = upload.receipt?.link?.toString(),
            uploadError = upload.error,
        )
    }

    private fun buildArchive(request: DebugRequest, encrypted: Boolean): ByteArray {
        val archive = SupportArchiveBuilder(config.maxReportBytes.toLong())
        addEnvironment(archive, request, encrypted)
        addPluginDiagnostics(archive, request.target)

        if (request.logs && config.logs) {
            addObservabilityData(archive)
            addDiagnosticLogs(archive)
            addDiagnosticHistory(archive)
        }
        if (request.configs && config.configs) {
            addConfigurations(archive, request.target, encrypted)
        }

        val bytes = archive.build()
        require(bytes.size <= config.maxReportBytes) {
            "Diagnostic archive exceeds configured limit (${bytes.size} > ${config.maxReportBytes} bytes)"
        }
        return bytes
    }

    private fun addEnvironment(
        archive: SupportArchiveBuilder,
        request: DebugRequest,
        encrypted: Boolean,
    ) {
        archive.json("manifest.json", linkedMapOf(
            "schemaVersion" to 3,
            "producer" to "pnLibrary",
            "generatedUtc" to Instant.now().toString(),
            "target" to request.target,
            "platform" to platformAdapter.id,
            "platformImplementation" to platformAdapter.implementationName,
            "encrypted" to encrypted,
        ))
        archive.json("system.json", systemCollector.collect(includeNetworkAddresses = encrypted))
        archive.text("threads.txt", systemCollector.threadDump())
        archive.json("platform.json", platformAdapter.diagnosticDetails(includeSensitive = encrypted))
    }

    private fun addPluginDiagnostics(archive: SupportArchiveBuilder, target: String) {
        diagnosticsRegistry.snapshot(target).forEach { (plugin, snapshot) ->
            archive.json("plugins/${safePath(plugin)}/diagnostics.json", snapshot)
        }
    }

    private fun addObservabilityData(archive: SupportArchiveBuilder) {
        val snapshot = observabilitySnapshot()
        if (snapshot.journal.isNotEmpty()) {
            archive.bytes("observability/observations.jsonl", snapshot.journal)
        }
        if (snapshot.attachmentManifest.isNotEmpty()) {
            archive.bytes("observability/attachments.json", snapshot.attachmentManifest)
        }
        snapshot.attachments.forEach { (name, bytes) ->
            archive.bytes("observability/attachments/$name", bytes)
        }
    }

    private fun addDiagnosticLogs(archive: SupportArchiveBuilder) {
        diagnosticLogs()
            .takeLast(config.logRecords.coerceIn(1, 2_000))
            .groupBy { log -> safePath(log["plugin"]?.toString() ?: "runtime") }
            .forEach { (plugin, logs) ->
                archive.json("plugins/$plugin/logs/incidents.json", logs)
            }
    }

    private fun addDiagnosticHistory(archive: SupportArchiveBuilder) {
        diagnosticHistory().forEach { (name, content) ->
            archive.bytes("history/${safePath(name)}", content)
        }
    }

    private fun addConfigurations(
        archive: SupportArchiveBuilder,
        target: String,
        encrypted: Boolean,
    ) {
        diagnosticsRegistry.configurations(target).forEach { registered ->
            val root = registered.dataDirectory ?: dataFolder
            val directory = "plugins/${safePath(registered.plugin)}/configuration/"
            if (encrypted) {
                val file = configReader.readExactFile(registered.configuration, root)
                val path = safeConfigurationPath(file.path)
                if (file.error == null) {
                    archive.bytes(directory + path, requireNotNull(file.content))
                } else {
                    archive.text(directory + "$path.error.txt", file.error + "\n")
                }
            } else {
                val file = configReader.readRedactedFile(registered.configuration, root)
                val path = safeConfigurationPath(file.path)
                if (file.error == null) {
                    archive.text(directory + path, file.content)
                } else {
                    archive.text(directory + "$path.error.txt", file.error + "\n")
                }
            }
        }
    }

    private fun encrypt(archive: ByteArray, encrypted: Boolean): ByteArray? {
        if (!encrypted) return null
        val codec = encryptionCodec
            ?: throw IllegalStateException("Encryption is enabled but codec is null")
        return codec.encryptBinary(archive, "zip")
    }

    private fun upload(
        reportFile: Path,
        encryptedPayload: ByteArray?,
        request: DebugRequest,
    ): UploadResult {
        if (request.local || !config.upload || uploader == null) return UploadResult()

        return try {
            requireNotNull(encryptedPayload) {
                "Binary plaintext archives are local-only; enable encrypted upload"
            }
            val receipt = uploader.uploadFile(reportFile, "application/vnd.pnfolder.support")
            uploadLedger?.record(receipt, config.deleteAfterDays)
            UploadResult(receipt = receipt)
        } catch (error: Exception) {
            UploadResult(error = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun safePath(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifBlank { "unknown" }.take(96)

    private fun safeConfigurationPath(value: String): String = value
        .replace('\\', '/')
        .split('/')
        .filter { it.isNotBlank() && it != "." && it != ".." }
        .joinToString("/") { component ->
            component.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(128).ifBlank { "config" }
        }.ifBlank { "config.txt" }

    private data class UploadResult(
        val receipt: UploadReceipt? = null,
        val error: String? = null,
    )
}
