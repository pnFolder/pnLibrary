package ru.privatenull.pnlibrary.core.observability.report

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import ru.privatenull.pnlibrary.core.upload.UploadProvider
import ru.privatenull.pnlibrary.core.upload.UploadLedger
import ru.privatenull.pnlibrary.core.upload.UploadReceipt
import ru.privatenull.pnlibrary.core.observability.ObservabilityReportSnapshot
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Path

/**
 * Coordinates collection, archive creation, encryption, local storage, and upload.
 *
 * Plaintext archives are always local-only. When `uploadMode` is `encrypted`, raw
 * registered configuration files and sensitive platform details may be included
 * because [EncryptedEnvelopeCodec] protects the complete ZIP before persistence
 * and upload. In plaintext mode, configurations pass through the secure
 * configuration reader before they enter the archive.
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
    diagnosticsRegistry: DiagnosticsRegistry,
    platformAdapter: PlatformAdapter,
    private val encryptionCodec: EncryptedEnvelopeCodec?,
    private val uploader: UploadProvider?,
    private val uploadLedger: UploadLedger?,
    diagnosticLogs: () -> List<Map<String, Any?>> = { emptyList() },
    diagnosticHistory: () -> List<Pair<String, ByteArray>> = { emptyList() },
    observabilitySnapshot: () -> ObservabilityReportSnapshot = {
        ObservabilityReportSnapshot(ByteArray(0), ByteArray(0), emptyMap(), emptyList())
    },
    runtimeDiagnostics: () -> Map<String, Any?> = { emptyMap() },
) {

    private val assembler = SupportReportAssembler(
        dataFolder = dataFolder,
        config = config,
        diagnostics = diagnosticsRegistry,
        platform = platformAdapter,
        diagnosticLogs = diagnosticLogs,
        diagnosticHistory = diagnosticHistory,
        observabilitySnapshot = observabilitySnapshot,
        runtimeDiagnostics = runtimeDiagnostics,
    )
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
        val archiveBytes = assembler.assemble(request, encrypted)
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

    private data class UploadResult(
        val receipt: UploadReceipt? = null,
        val error: String? = null,
    )
}
