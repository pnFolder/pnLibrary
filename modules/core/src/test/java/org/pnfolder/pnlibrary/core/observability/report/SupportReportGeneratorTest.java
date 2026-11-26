package org.pnfolder.pnlibrary.core.observability.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportReportGeneratorTest {
    @TempDir
    Path temporary;

    private SupportReportGenerator generator(DiagnosticsRegistry registry,
                                             java.util.function.Supplier<java.util.List<Map<String, Object>>> logs)
            throws IOException {
        TestPlatform platform = new TestPlatform("pnlibrary-report-test");
        return new SupportReportGenerator(temporary, temporary.resolve("reports"),
                PnLibraryConfig.builder().upload(false).uploadMode("disabled").build(), registry, platform, platform,
                null, null, null, logs);
    }

    private static Set<String> names(DiagnosticReport result) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(result.getLocalFile().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }

    @Test
    void fullAllReportIncludesEveryPluginConfigAndBufferedLogs() throws IOException {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();
        for (String plugin : Arrays.asList("pnMarket", "pnClans")) {
            Path folder = temporary.resolve(plugin);
            Files.createDirectories(folder);
            Files.write(folder.resolve("config.yml"), "enabled: true\n".getBytes());
            registry.register(plugin, folder, DiagnosticContainer.builder(plugin).configuration("config.yml").build());
        }
        SupportReportGenerator generator = generator(registry, () -> Collections.singletonList(
                Collections.<String, Object>singletonMap("message", "test")));

        Set<String> names = names(generator.generateAndSave(new DebugRequest("all", true, true, true)));

        assertTrue(names.contains("pn-diagnostic/plugins/pnmarket/configuration/config.yml"));
        assertTrue(names.contains("pn-diagnostic/plugins/pnclans/configuration/config.yml"));
        assertTrue(names.contains("pn-diagnostic/plugins/runtime/logs/incidents.json"));
        assertTrue(names.contains("pn-diagnostic/checksums.json"));
    }

    @Test
    void configFilesWhoseNamesCollideAfterCleaningGetDistinctArchiveEntries() throws IOException {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();
        Path folder = temporary.resolve("pnMarket");
        Files.createDirectories(folder);
        Files.write(folder.resolve("./config.yml"), "x: 1\n".getBytes());
        Files.write(folder.resolve("config.yml"), "x: 2\n".getBytes());
        registry.register("pnMarket", folder, DiagnosticContainer.builder("pnMarket")
                .configuration("./config.yml").configuration("config.yml").build());
        SupportReportGenerator generator = generator(registry, Collections::emptyList);

        Set<String> names = names(generator.generateAndSave(new DebugRequest("all", true, false, true)));

        assertTrue(names.contains("pn-diagnostic/plugins/pnmarket/configuration/config.yml"));
        assertTrue(names.contains("pn-diagnostic/plugins/pnmarket/configuration/config-2.yml"));
    }
}
