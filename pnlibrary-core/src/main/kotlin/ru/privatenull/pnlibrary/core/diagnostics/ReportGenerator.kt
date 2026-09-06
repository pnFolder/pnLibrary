package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import ru.privatenull.pnlibrary.core.upload.MultipartUploader
import ru.privatenull.pnlibrary.core.upload.ReportUploader
import ru.privatenull.pnlibrary.core.upload.UploadLedger
import ru.privatenull.pnlibrary.core.upload.UploadReceipt


import com.google.gson.Gson
import com.google.gson.GsonBuilder
import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Main orchestrator for collecting, redacting, formatting, saving, and uploading diagnostic reports.
 */
class ReportGenerator(
    private val dataFolder: Path,
    private val config: PnLibraryConfig,
    private val diagnosticsRegistry: DiagnosticsRegistry,
    private val platformAdapter: PlatformAdapter,
    private val encryptionCodec: EncryptedEnvelopeCodec?,
    private val uploader: ReportUploader?,
    private val uploadLedger: UploadLedger?,
) {

    private val systemCollector = SystemCollector()
    private val configReader = ConfigReader(dataFolder, config)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun generateAndSave(request: DebugRequest): ReportResult {
        val encryptionMode = config.uploadMode.startsWith("encrypted")
        val includeNetworkAddresses = encryptionMode

        val reportData = linkedMapOf<String, Any?>()
        reportData["schemaVersion"] = 2
        reportData["producer"] = "pnLibrary"
        reportData["generatedUtc"] = Instant.now().toString()
        reportData["target"] = request.target
        reportData["platform"] = platformAdapter.id

        // Platform & System info
        reportData["platformDetails"] = platformAdapter.details()
        reportData["system"] = systemCollector.collect(includeNetworkAddresses = includeNetworkAddresses)

        // Plugin diagnostic containers & status entries
        reportData["pluginDiagnostics"] = diagnosticsRegistry.snapshot(request.target)

        // Config files
        if (request.configs && config.configs) {
            val configsList = mutableListOf<Map<String, Any?>>()
            val declaredConfigs = diagnosticsRegistry.configurations(request.target)
            for (registered in declaredConfigs) {
                configsList.add(configReader.readAndRedact(
                    registered.configuration,
                    registered.dataDirectory ?: dataFolder,
                ))
            }
            reportData["configurations"] = configsList
        }

        // Encode JSON with truncation guard
        val textReport = encodeWithTruncation(reportData, config.maxReportBytes)

        // Encrypt if encryption mode is active
        val finalPayload = if (encryptionMode) {
            val codec = encryptionCodec ?: throw IllegalStateException("Encryption is enabled but codec is null")
            codec.encrypt(textReport)
        } else {
            if (!config.allowPlaintext && "mclogs" == config.uploadMode) {
                throw IllegalStateException("Plaintext reports are disallowed in current config")
            }
            textReport
        }

        // Save local file
        val reportsDir = dataFolder.resolve("reports")
        Files.createDirectories(reportsDir)
        val fileExtension = if (encryptionMode) ".pndebug" else ".txt"
        val timestamp = System.currentTimeMillis()
        val targetFile = reportsDir.resolve("report-$timestamp$fileExtension")
        Files.write(targetFile, finalPayload.toByteArray(StandardCharsets.UTF_8))

        // Cleanup old local reports
        cleanupOldReports(reportsDir, config.keepReports)

        // Upload if enabled and not local-only
        var uploadReceipt: UploadReceipt? = null
        if (!request.local && config.upload && uploader != null) {
            val multipartUploader = MultipartUploader(uploader, encryptionCodec, uploadLedger, config.deleteAfterDays)
            uploadReceipt = multipartUploader.upload(finalPayload)
            uploadLedger?.record(uploadReceipt, config.deleteAfterDays)
        }

        return ReportResult(
            localFile = targetFile,
            isEncrypted = encryptionMode,
            uploadReceipt = uploadReceipt
        )
    }

    private fun encodeWithTruncation(report: Map<String, Any?>, maxBytes: Int): String {
        var json = gson.toJson(report)
        if (json.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return json

        val reduced = LinkedHashMap(report)
        reduced["truncated"] = "Report exceeded byte limit; configurations omitted."
        reduced.remove("configurations")
        json = gson.toJson(reduced)
        if (json.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return json

        reduced["truncated"] = "Report exceeded byte limit; plugin diagnostics omitted."
        reduced.remove("pluginDiagnostics")
        json = gson.toJson(reduced)
        if (json.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return json

        val minimal = linkedMapOf<String, Any?>()
        minimal["schemaVersion"] = report["schemaVersion"]
        minimal["producer"] = report["producer"]
        minimal.put("generatedUtc", report["generatedUtc"])
        minimal["target"] = report["target"]
        minimal["system"] = report["system"]
        minimal["truncated"] = "Report exceeded configured byte limit."
        return gson.toJson(minimal)
    }

    private fun cleanupOldReports(dir: Path, keepCount: Int) {
        try {
            val files = Files.list(dir)
                .filter { Files.isRegularFile(it) }
                .sorted { p1, p2 -> Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1)) }
                .toList()

            if (files.size > keepCount) {
                for (i in keepCount until files.size) {
                    runCatching { Files.deleteIfExists(files[i]) }
                }
            }
        } catch (_: Exception) { }
    }

    data class ReportResult(
        val localFile: Path,
        val isEncrypted: Boolean,
        val uploadReceipt: UploadReceipt?,
    )
}
