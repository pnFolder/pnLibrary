package org.pnfolder.pnlibrary.core.observability.report;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.core.diagnostics.config.ConfigReader;
import org.pnfolder.pnlibrary.spi.platform.PlatformDiagnosticReport;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Collects runtime information and assembles the plaintext support ZIP. */
public final class SupportReportAssembler {
    private final PnLibraryConfig config;
    private final DiagnosticsRegistry diagnostics;
    private final PlatformIdentity identity;
    private final PlatformDiagnosticReport diagnosticsPlatform;
    private final Supplier<List<Map<String, Object>>> diagnosticLogs;
    private final Supplier<List<Map.Entry<String, byte[]>>> diagnosticHistory;
    private final Supplier<ObservabilityReportSnapshot> observabilitySnapshot;
    private final Supplier<Map<String, Object>> runtimeDiagnostics;
    private final SystemReportCollector system = new SystemReportCollector();
    private final ConfigurationArchiveSection configurations;

    /** An assembler without runtime diagnostics. */
    public SupportReportAssembler(@NotNull Path dataFolder, @NotNull PnLibraryConfig config,
                                  @NotNull DiagnosticsRegistry diagnostics, @NotNull PlatformIdentity identity,
                                  @NotNull PlatformDiagnosticReport diagnosticsPlatform,
                                  @NotNull Supplier<List<Map<String, Object>>> diagnosticLogs,
                                  @NotNull Supplier<List<Map.Entry<String, byte[]>>> diagnosticHistory,
                                  @NotNull Supplier<ObservabilityReportSnapshot> observabilitySnapshot) {
        this(dataFolder, config, diagnostics, identity, diagnosticsPlatform, diagnosticLogs, diagnosticHistory,
                observabilitySnapshot, Collections::emptyMap);
    }

    /**
     * Creates the assembler.
     *
     * @param dataFolder plugin data folder, the default root for configuration files
     * @param diagnosticLogs supplies recent bounded diagnostic incidents
     * @param diagnosticHistory supplies previously persisted incident snapshots
     * @param runtimeDiagnostics supplies the runtime section of the manifest
     */
    public SupportReportAssembler(@NotNull Path dataFolder, @NotNull PnLibraryConfig config,
                                  @NotNull DiagnosticsRegistry diagnostics, @NotNull PlatformIdentity identity,
                                  @NotNull PlatformDiagnosticReport diagnosticsPlatform,
                                  @NotNull Supplier<List<Map<String, Object>>> diagnosticLogs,
                                  @NotNull Supplier<List<Map.Entry<String, byte[]>>> diagnosticHistory,
                                  @NotNull Supplier<ObservabilityReportSnapshot> observabilitySnapshot,
                                  @NotNull Supplier<Map<String, Object>> runtimeDiagnostics) {
        this.config = Objects.requireNonNull(config, "config");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.diagnosticsPlatform = Objects.requireNonNull(diagnosticsPlatform, "diagnosticsPlatform");
        this.diagnosticLogs = Objects.requireNonNull(diagnosticLogs, "diagnosticLogs");
        this.diagnosticHistory = Objects.requireNonNull(diagnosticHistory, "diagnosticHistory");
        this.observabilitySnapshot = Objects.requireNonNull(observabilitySnapshot, "observabilitySnapshot");
        this.runtimeDiagnostics = runtimeDiagnostics;
        this.configurations = new ConfigurationArchiveSection(dataFolder, diagnostics, new ConfigReader(dataFolder, config));
    }

    /** Builds the archive for {@code request}; fails when it would exceed the configured size. */
    @NotNull
    public byte[] assemble(@NotNull DebugRequest request, boolean encrypted) {
        SupportArchiveBuilder archive = new SupportArchiveBuilder(config.getMaxReportBytes());
        addEnvironment(archive, request, encrypted);
        for (Map.Entry<String, Object> plugin : diagnostics.snapshot(request.getTarget()).entrySet()) {
            archive.json("plugins/" + ArchivePaths.folderOf(plugin.getKey()) + "/diagnostics.json", plugin.getValue());
        }
        if (request.isLogs() && config.isLogs()) {
            addObservabilityData(archive);
            addDiagnosticLogs(archive);
            for (Map.Entry<String, byte[]> file : diagnosticHistory.get()) {
                archive.bytes("history/" + ArchivePaths.safePath(file.getKey()), file.getValue());
            }
        }
        if (request.isConfigs() && config.isConfigs()) {
            configurations.addTo(archive, request.getTarget(), encrypted);
        }
        byte[] bytes = archive.build();
        if (bytes.length > config.getMaxReportBytes()) {
            throw new IllegalArgumentException("Diagnostic archive exceeds configured limit ("
                    + bytes.length + " > " + config.getMaxReportBytes() + " bytes)");
        }
        return bytes;
    }

    private void addEnvironment(SupportArchiveBuilder archive, DebugRequest request, boolean encrypted) {
        Map<String, Object> platformDetails = diagnosticsPlatform.diagnosticDetails(encrypted && !config.isPrivacy());
        Map<String, Object> runtimeDetails = runtimeDiagnostics.get();
        Map<String, Object> systemDetails = system.collect();
        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("generatedUtc", Instant.now().toString());
        analytics.put("system", systemDetails.get("health"));
        analytics.put("platform", platformDetails.get("health"));
        analytics.put("runtime", runtimeDetails.get("analytics"));
        analytics.put("metrics", runtimeDetails.get("metricsAnalytics"));
        analytics.put("observability", runtimeDetails.get("observability"));
        archive.json("manifest.json", manifest(request, encrypted));
        if (!runtimeDetails.isEmpty()) {
            archive.json("runtime.json", runtimeDetails);
        }
        archive.json("analytics.json", analytics);
        archive.json("system.json", systemDetails);
        archive.text("threads.txt", system.threadDump());
        archive.json("platform.json", platformDetails);
    }

    private Map<String, Object> manifest(DebugRequest request, boolean encrypted) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 4);
        manifest.put("producer", PnLibraryIdentity.NAME);
        manifest.put("generatedUtc", Instant.now().toString());
        manifest.put("target", request.getTarget());
        manifest.put("platform", identity.getId());
        manifest.put("platformImplementation", identity.getImplementationName());
        manifest.put("encrypted", encrypted);
        manifest.put("logs", request.isLogs() && config.isLogs());
        manifest.put("configs", request.isConfigs() && config.isConfigs());
        manifest.put("privacy", config.isPrivacy());
        return manifest;
    }

    private void addObservabilityData(SupportArchiveBuilder archive) {
        ObservabilityReportSnapshot snapshot = observabilitySnapshot.get();
        if (!snapshot.getAnalytics().isEmpty()) {
            archive.json("observability/analytics.json", snapshot.getAnalytics());
        }
        if (!snapshot.getStatuses().isEmpty()) {
            archive.json("observability/statuses.json", snapshot.getStatuses());
        }
        if (snapshot.getJournal().length > 0) {
            archive.bytes("observability/observations.jsonl", snapshot.getJournal());
        }
        if (snapshot.getAttachmentManifest().length > 0) {
            archive.bytes("observability/attachments.json", snapshot.getAttachmentManifest());
        }
        for (Map.Entry<String, byte[]> attachment : snapshot.getAttachments().entrySet()) {
            archive.bytes("observability/attachments/" + attachment.getKey(), attachment.getValue());
        }
    }

    private void addDiagnosticLogs(SupportArchiveBuilder archive) {
        List<Map<String, Object>> logs = diagnosticLogs.get();
        int limit = Math.max(1, Math.min(config.getLogRecords(), 2_000));
        Map<String, List<Map<String, Object>>> byPlugin = new LinkedHashMap<>();
        for (Map<String, Object> log : logs.subList(Math.max(0, logs.size() - limit), logs.size())) {
            Object plugin = log.get("plugin");
            String folder = ArchivePaths.safePath(plugin == null ? "runtime" : plugin.toString());
            byPlugin.computeIfAbsent(folder, key -> new java.util.ArrayList<>()).add(log);
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : byPlugin.entrySet()) {
            archive.json("plugins/" + entry.getKey() + "/logs/incidents.json", entry.getValue());
        }
    }
}
