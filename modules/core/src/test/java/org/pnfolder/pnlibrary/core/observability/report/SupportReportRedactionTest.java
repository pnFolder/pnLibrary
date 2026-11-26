package org.pnfolder.pnlibrary.core.observability.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportReportRedactionTest {
    @TempDir
    Path temporary;

    private String report(String yaml, Consumer<DiagnosticConfiguration.Builder> build) throws IOException {
        Path folder = temporary.resolve("pnTest");
        Files.createDirectories(folder);
        Files.write(folder.resolve("config.yml"), yaml.getBytes(StandardCharsets.UTF_8));
        DiagnosticsRegistry registry = new DiagnosticsRegistry();
        DiagnosticConfiguration.Builder builder = DiagnosticConfiguration.builder("config.yml");
        build.accept(builder);
        registry.register("pnTest", folder, DiagnosticContainer.builder("pnTest").configuration(builder.build()).build());
        TestPlatform platform = new TestPlatform("pnlibrary-redaction-test");
        byte[] bytes = new SupportReportAssembler(temporary,
                PnLibraryConfig.builder().upload(false).uploadMode("disabled").build(), registry, platform, platform,
                Collections::emptyList, Collections::emptyList, ObservabilityReportSnapshot::empty)
                .assemble(new DebugRequest("all", true, false, true), false);
        Path file = temporary.resolve("report.zip");
        Files.write(file, bytes);
        StringBuilder text = new StringBuilder();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith("config.yml")) {
                    text.append(text.length() > 0 ? "\n" : "").append(read(zip.getInputStream(entry)));
                }
            }
        }
        return text.toString();
    }

    private static String read(InputStream input) throws IOException {
        try (InputStream stream = input) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            for (int count = stream.read(chunk); count >= 0; count = stream.read(chunk)) {
                out.write(chunk, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void excludedPathsAndSecretKeyPatternsNeverReachTheReport() throws IOException {
        String text = report("db:\n  host: localhost\n  pass: hunter2xyz\napi: tokenvalue123\nname: visible-name\n",
                builder -> builder.exclude("db.pass").secretKeyRegex("^api$"));
        assertFalse(text.contains("hunter2xyz"), text);
        assertFalse(text.contains("tokenvalue123"), text);
        assertTrue(text.contains("visible-name"), text);
    }

    @Test
    void aValuePatternHidesTheMatchingPart() throws IOException {
        String text = report("note: leak-me-please\n", builder -> builder.redactValueRegex("leak"));
        assertFalse(text.contains("leak"), text);
        assertTrue(text.contains("REDACTED"), text);
    }
}
