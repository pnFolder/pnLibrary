package org.pnfolder.pnlibrary.core.observability.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.core.upload.UploadLedger;
import org.pnfolder.pnlibrary.core.upload.UploadProvider;
import org.pnfolder.pnlibrary.core.upload.UploadReceipt;
import org.pnfolder.pnlibrary.spi.platform.PlatformDiagnosticReport;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Coordinates collection, archive creation, encryption, local storage, and upload.
 *
 * <p>Plaintext archives are always local-only. When {@code uploadMode} is {@code encrypted}, raw registered
 * configuration files and sensitive platform details may be included because {@link EncryptedEnvelopeCodec} protects
 * the complete ZIP before persistence and upload. In plaintext mode, configurations pass through the secure
 * configuration reader before they enter the archive.
 *
 * <p>Report assembly is fail-fast for local collection and encryption errors. Upload failures are instead returned in
 * {@link DiagnosticReport#getUploadError()}, preserving the successfully written local report.
 */
public final class SupportReportGenerator {
    private final PnLibraryConfig config;
    private final EncryptedEnvelopeCodec encryptionCodec;
    private final UploadProvider uploader;
    private final UploadLedger uploadLedger;
    private final SupportReportAssembler assembler;
    private final SupportReportStore reportStore;

    /** A generator without persisted history, observability data or runtime diagnostics. */
    public SupportReportGenerator(@NotNull Path dataFolder, @NotNull Path reportsFolder, @NotNull PnLibraryConfig config,
                                  @NotNull DiagnosticsRegistry diagnosticsRegistry,
                                  @NotNull PlatformIdentity platformIdentity,
                                  @NotNull PlatformDiagnosticReport platformDiagnostics,
                                  @Nullable EncryptedEnvelopeCodec encryptionCodec, @Nullable UploadProvider uploader,
                                  @Nullable UploadLedger uploadLedger,
                                  @NotNull Supplier<List<Map<String, Object>>> diagnosticLogs) {
        this(dataFolder, reportsFolder, config, diagnosticsRegistry, platformIdentity, platformDiagnostics,
                encryptionCodec, uploader, uploadLedger, diagnosticLogs,
                Collections::<Map.Entry<String, byte[]>>emptyList, ObservabilityReportSnapshot::empty,
                Collections::<String, Object>emptyMap);
    }

    /**
     * Creates the generator.
     *
     * @param dataFolder plugin data folder, the default root for configuration files
     * @param reportsFolder where the generated reports are stored
     * @param config immutable diagnostic and upload policy
     * @param diagnosticsRegistry source of plugin snapshots and configuration declarations
     * @param encryptionCodec envelope codec required in encrypted mode
     * @param uploader optional remote destination or ordered provider chain
     * @param uploadLedger optional persistence for remote deletion receipts
     * @param diagnosticLogs supplies recent bounded diagnostic incidents
     * @param diagnosticHistory supplies previously persisted incident snapshots
     */
    public SupportReportGenerator(@NotNull Path dataFolder, @NotNull Path reportsFolder, @NotNull PnLibraryConfig config,
                                  @NotNull DiagnosticsRegistry diagnosticsRegistry,
                                  @NotNull PlatformIdentity platformIdentity,
                                  @NotNull PlatformDiagnosticReport platformDiagnostics,
                                  @Nullable EncryptedEnvelopeCodec encryptionCodec, @Nullable UploadProvider uploader,
                                  @Nullable UploadLedger uploadLedger,
                                  @NotNull Supplier<List<Map<String, Object>>> diagnosticLogs,
                                  @NotNull Supplier<List<Map.Entry<String, byte[]>>> diagnosticHistory,
                                  @NotNull Supplier<ObservabilityReportSnapshot> observabilitySnapshot,
                                  @NotNull Supplier<Map<String, Object>> runtimeDiagnostics) {
        this.config = config;
        this.encryptionCodec = encryptionCodec;
        this.uploader = uploader;
        this.uploadLedger = uploadLedger;
        this.assembler = new SupportReportAssembler(dataFolder, config, diagnosticsRegistry, platformIdentity,
                platformDiagnostics, diagnosticLogs, diagnosticHistory, observabilitySnapshot, runtimeDiagnostics);
        this.reportStore = new SupportReportStore(reportsFolder);
    }

    /**
     * Generates one report according to {@code request}, stores it locally, and optionally uploads the encrypted artifact.
     *
     * @throws IllegalStateException when encrypted mode lacks an encryption codec
     * @throws IllegalArgumentException when the assembled archive exceeds the configured limit
     * @throws IOException when local collection or persistence fails
     */
    @NotNull
    public DiagnosticReport generateAndSave(@NotNull DebugRequest request) throws IOException {
        boolean encrypted = "encrypted".equals(config.getUploadMode());
        byte[] archiveBytes = assembler.assemble(request, encrypted);
        byte[] encryptedPayload = encrypt(archiveBytes, encrypted);
        Path reportFile = reportStore.save(encryptedPayload != null ? encryptedPayload : archiveBytes, encrypted,
                config.getKeepReports());
        UploadResult upload = upload(reportFile, encryptedPayload, request);
        return new DiagnosticReport(reportFile, encrypted,
                upload.receipt == null ? null : upload.receipt.getLink().toString(), upload.error);
    }

    private byte[] encrypt(byte[] archive, boolean encrypted) throws IOException {
        if (!encrypted) {
            return null;
        }
        if (encryptionCodec == null) {
            throw new IllegalStateException("Encryption is enabled but codec is null");
        }
        return encryptionCodec.encryptBinary(archive, "zip");
    }

    private UploadResult upload(Path reportFile, byte[] encryptedPayload, DebugRequest request) {
        if (request.isLocal() || !config.isUpload() || uploader == null) {
            return new UploadResult(null, null);
        }
        try {
            if (encryptedPayload == null) {
                throw new IllegalArgumentException("Binary plaintext archives are local-only; enable encrypted upload");
            }
            UploadReceipt receipt = uploader.uploadFile(reportFile, "application/vnd.pnfolder.support");
            if (uploadLedger != null) {
                uploadLedger.record(receipt, config.getDeleteAfterDays());
            }
            return new UploadResult(receipt, null);
        } catch (Exception failure) {
            String message = failure.getMessage();
            return new UploadResult(null, message != null ? message : failure.getClass().getSimpleName());
        }
    }
}
