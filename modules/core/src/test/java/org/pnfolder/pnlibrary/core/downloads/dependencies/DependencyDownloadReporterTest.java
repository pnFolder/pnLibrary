package org.pnfolder.pnlibrary.core.downloads.dependencies;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

class DependencyDownloadReporterTest {
    private PlatformAdapter platformInto(List<String> output, PlatformType type) {
        return (PlatformAdapter) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {PlatformAdapter.class}, (proxy, method, arguments) -> {
                switch (method.getName()) {
                    case "getType":
                        return type;
                    case "ownerDetails":
                        return Collections.singletonMap("name", "Demo");
                    case "console":
                        output.add((String) arguments[1]);
                        return null;
                    case "details":
                        return Collections.emptyMap();
                    default:
                        return null;
                }
            });
    }

    private MissingGroup group() {
        DownloadRegistration registration = (DownloadRegistration) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {DownloadRegistration.class}, (proxy, method, arguments) -> null);
        MissingDependency vault = new MissingDependency("Vault", "1.7.0", null,
            Arrays.asList(URI.create("https://example.org/Vault.jar")));
        return new MissingGroup("Demo", "abc123", Arrays.asList(vault), registration);
    }

    private static boolean any(List<String> output, String text) {
        for (String line : output) {
            if (line.contains(text)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void rendersStagedDependencyAndHumanReadableFailureCards() {
        List<String> output = new ArrayList<>();
        PlatformAdapter platform = platformInto(output, PlatformType.BUKKIT);
        DependencyDownloadReporter reporter = new DependencyDownloadReporter(platform, platform);
        FileDownload declaration = FileDownloads.builder().file("dependency:Vault", file -> file
            .url("https://example.org/Vault.jar")
            .destination(DownloadDestination.DATA_FOLDER, "Vault.jar")).build().getFiles().get(0);

        reporter.staged(new Object(), Arrays.asList(declaration));
        reporter.failure(new Object(),
            new IllegalArgumentException("скачанный JAR объявляет Wrong, ожидался Vault"), LogLevel.ERROR);

        assertTrue(any(output, "Vault.jar"));
        assertTrue(any(output, "В скачанном файле указано имя Wrong"));
    }

    @Test
    void theCommandInTheAdviceIsTheOneThatWorksOnThisPlatform() {
        List<String> onProxy = new ArrayList<>();
        PlatformAdapter proxyPlatform = platformInto(onProxy, PlatformType.VELOCITY);
        new DependencyDownloadReporter(proxyPlatform, proxyPlatform, "pnproxy").missing(new Object(), group(), false);
        List<String> onServer = new ArrayList<>();
        PlatformAdapter serverPlatform = platformInto(onServer, PlatformType.VELOCITY);
        new DependencyDownloadReporter(serverPlatform, serverPlatform).missing(new Object(), group(), false);

        assertTrue(any(onProxy, "pnproxy deps install abc123"));
        assertFalse(any(onProxy, "deps auto"));
        boolean serverAdvice = false;
        for (String line : onServer) {
            serverAdvice |= line.contains("pn deps install abc123") && !line.contains("pnproxy");
        }
        assertTrue(serverAdvice);
    }

    @Test
    void theNoticeTellsWhatEachPluginIsForWhetherItIsRequiredAndHowItsFileIsChecked() {
        List<String> output = new ArrayList<>();
        DownloadRegistration registration = (DownloadRegistration) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {DownloadRegistration.class}, (proxy, method, arguments) -> null);
        MissingGroup group = new MissingGroup("Demo", "abc123", Arrays.asList(
            new MissingDependency("Vault", "1.7.0", null, Arrays.asList(
                URI.create("https://example.org/Vault.jar"), URI.create("https://mirror.example.org/Vault.jar")))
                .withTitle("Экономика").withDescription("Нужна для денег и магазинов.")
                .withRequired(true).withChecked(true),
            new MissingDependency("Essentials", "2.20.0", URI.create("https://example.org/essentials"),
                Collections.emptyList()).withRequired(false)), registration);

        PlatformAdapter platform = platformInto(output, PlatformType.VELOCITY);
        new DependencyDownloadReporter(platform, platform).missing(new Object(), group, false);

        String text = String.join(" ", output);
        assertTrue(text.contains("Обязательные"));
        assertTrue(text.contains("Необязательные"));
        assertTrue(text.contains("Экономика (Vault) 1.7.0+"));
        assertTrue(text.contains("Нужна для денег и магазинов."));
        assertTrue(text.contains("mirror.example.org/Vault.jar"));
        assertTrue(text.contains("SHA-256 автора"));
        assertTrue(text.contains("Essentials 2.20.0+"));
    }

    @Test
    void everyCardNamesThePluginWithItsVersionAndEndsWithTheSupportLink() {
        List<String> output = new ArrayList<>();
        PlatformAdapter platform = (PlatformAdapter) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {PlatformAdapter.class}, (proxy, method, arguments) -> {
                switch (method.getName()) {
                    case "getType":
                        return PlatformType.BUKKIT;
                    case "ownerDetails":
                        java.util.Map<String, String> details = new java.util.HashMap<>();
                        details.put("name", "Demo");
                        details.put("version", "1.4.0");
                        return details;
                    case "console":
                        output.add((String) arguments[1]);
                        return null;
                    default:
                        return null;
                }
            });
        DependencyDownloadReporter reporter = new DependencyDownloadReporter(platform, platform);
        reporter.failure(new Object(), new IllegalArgumentException("x"), LogLevel.ERROR);
        reporter.missing(new Object(), group(), false);
        reporter.connected(new Object(), Arrays.asList(new DownloadedDependency("Vault", "1.7.3")));

        assertTrue(any(output, "Demo@main 1.4.0"));
        int supports = 0;
        for (String line : output) {
            supports += line.contains("Вопросы и ошибки: https://discord.gg/") ? 1 : 0;
        }
        assertTrue(supports == 3, "support links: " + supports);
    }
}
