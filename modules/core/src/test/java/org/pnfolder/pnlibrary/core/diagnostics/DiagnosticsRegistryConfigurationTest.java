package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;

class DiagnosticsRegistryConfigurationTest {
    @TempDir
    Path temporary;

    @Test
    void allReturnsConfigurationsFromEveryPluginWithoutMergingIdenticalPaths() {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();
        Path first = temporary.resolve("first");
        Path second = temporary.resolve("second");
        registry.register("pnMarket", first, DiagnosticContainer.builder("market").configuration("config.yml").build());
        registry.register("pnClans", second, DiagnosticContainer.builder("clans").configuration("config.yml").build());

        List<RegisteredDiagnosticConfiguration> configurations = registry.configurations("all");

        assertEquals(2, configurations.size());
        Set<String> plugins = new HashSet<>();
        Set<Path> directories = new HashSet<>();
        for (RegisteredDiagnosticConfiguration configuration : configurations) {
            plugins.add(configuration.getPlugin());
            directories.add(configuration.getDataDirectory());
        }
        assertEquals(new HashSet<>(Arrays.asList("pnmarket", "pnclans")), plugins);
        assertEquals(new HashSet<>(Arrays.asList(first.toAbsolutePath(), second.toAbsolutePath())), directories);
    }

    @Test
    void diagnosticSummaryAggregatesIncidentsWithoutCollectingContributors() {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();

        registry.record("pnMarket", DiagnosticLevel.WARNING, "database", "TIMEOUT", "retrying");
        registry.record("pnMarket", DiagnosticLevel.WARNING, "database", "TIMEOUT", "retrying");
        registry.record("pnMarket", DiagnosticLevel.ERROR, "database", "DOWN", "unavailable");

        List<?> plugins = (List<?>) registry.diagnosticSummary().get("plugins");
        assertEquals(1, plugins.size());
        Map<?, ?> plugin = (Map<?, ?>) plugins.get(0);
        Map<?, ?> incidents = (Map<?, ?>) plugin.get("incidents");

        assertEquals(1, registry.diagnosticSummary().get("registeredPlugins"));
        assertEquals(2, incidents.get("incidentCount"));
        assertEquals(3L, incidents.get("occurrenceCount"));
        assertEquals(1, ((Map<?, ?>) incidents.get("byLevel")).get("WARNING"));
    }

    @Test
    void historyAnalyticsExposesBoundedIncidentTimeline() {
        DiagnosticsRegistry registry = new DiagnosticsRegistry();

        registry.record("pnMarket", DiagnosticLevel.WARNING, "database", "TIMEOUT", "retrying");
        registry.record("pnMarket", DiagnosticLevel.ERROR, "database", "DOWN", "unavailable");

        Map<String, Object> history = registry.historyAnalytics();

        assertEquals(2, history.get("incidentCount"));
        assertEquals(2L, history.get("occurrenceCount"));
        assertEquals(2, ((List<?>) history.get("recent")).size());
        assertEquals(1, ((Map<?, ?>) history.get("byLevel")).get("ERROR"));
    }
}
