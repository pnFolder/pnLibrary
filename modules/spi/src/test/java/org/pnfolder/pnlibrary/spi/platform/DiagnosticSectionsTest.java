package org.pnfolder.pnlibrary.spi.platform;

import org.pnfolder.pnlibrary.spi.platform.diagnostics.DiagnosticSections;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticSectionsTest {
    @Test
    void percentileUsesTheNearestRankSoOneSlowPlayerIsNotHidden() {
        assertEquals(500L, DiagnosticSections.percentile(Arrays.asList(10L, 500L), 0.95));
        assertEquals(20L, DiagnosticSections.percentile(Arrays.asList(10L, 20L, 30L, 40L), 0.5));
        assertEquals(7L, DiagnosticSections.percentile(Arrays.asList(7L), 0.95));
        assertNull(DiagnosticSections.percentile(Collections.emptyList(), 0.95));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticSections.percentile(Arrays.asList(1L), 1.5));
    }

    @Test
    void pingSummaryReportsSampleCountAndStatistics() {
        Map<String, Object> summary = DiagnosticSections.pingSummary(Arrays.asList(10L, 30L, 500L));
        assertEquals(3, summary.get("samples"));
        assertEquals(10L, summary.get("min"));
        assertEquals(500L, summary.get("max"));
        assertEquals(500L, summary.get("p95"));
        assertEquals(180.0, summary.get("average"));
        assertNull(DiagnosticSections.pingSummary(Collections.emptyList()).get("average"));
    }

    @Test
    void missingDependenciesAreReportedPerPluginInSortedOrder() {
        Map<String, List<String>> required = new LinkedHashMap<>();
        required.put("A", Arrays.asList("Vault", "ProtocolLib"));
        required.put("B", Arrays.asList("Vault"));
        required.put("C", Collections.emptyList());

        List<Map<String, Object>> result = DiagnosticSections.missingDependencies(
                required, new LinkedHashSet<>(Arrays.asList("Vault", "A", "B", "C")));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("plugin", "A");
        expected.put("missing", Arrays.asList("ProtocolLib"));
        assertEquals(Collections.singletonList(expected), result);
    }

    @Test
    void healthIsAttentionOnlyWhenASignalExists() {
        assertEquals("healthy", DiagnosticSections.health(Collections.emptyList()).get("status"));
        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("plugin", "A");
        missing.put("missing", Arrays.asList("Vault"));
        List<Map<String, Object>> signals = DiagnosticSections.commonSignals(
                Collections.singletonList(missing), Arrays.asList(400L));
        List<Object> codes = new ArrayList<>();
        for (Map<String, Object> signal : signals) {
            codes.add(signal.get("code"));
        }
        assertEquals(Arrays.asList("missingRequiredDependencies", "highPlayerLatency"), codes);
        assertEquals("attention", DiagnosticSections.health(signals).get("status"));
    }

    @Test
    void proxySectionsCountPlayersPerBackendAndReadAddressesOnlyForSensitiveReports() {
        AtomicInteger addressReads = new AtomicInteger();
        ProxyBackend lobby = new ProxyBackend("lobby", 2, () -> {
            addressReads.incrementAndGet();
            return InetSocketAddress.createUnresolved("backend.internal", 25565);
        });

        Map<String, Object> publicDetails = sections(lobby, false);
        assertEquals(Arrays.asList("missingRequiredDependencies", "backends", "players", "health"),
                new ArrayList<>(publicDetails.keySet()));
        Map<?, ?> publicBackend = (Map<?, ?>) ((List<?>) publicDetails.get("backends")).get(0);
        assertFalse(publicBackend.containsKey("host"));
        assertEquals(0, addressReads.get());
        Map<?, ?> players = (Map<?, ?>) publicDetails.get("players");
        assertEquals(3, players.get("online"));
        Map<String, Integer> byBackend = new LinkedHashMap<>();
        byBackend.put("lobby", 2);
        byBackend.put("[unassigned]", 1);
        assertEquals(byBackend, players.get("byBackend"));
        assertEquals(2, ((Map<?, ?>) players.get("pingMs")).get("samples"));
        assertEquals("attention", ((Map<?, ?>) publicDetails.get("health")).get("status"));

        Map<?, ?> backend = (Map<?, ?>) ((List<?>) sections(lobby, true).get("backends")).get(0);
        assertEquals("backend.internal", backend.get("host"));
        assertEquals(25565, backend.get("port"));
    }

    private static Map<String, Object> sections(ProxyBackend lobby, boolean sensitive) {
        Map<String, Object> details = new LinkedHashMap<>();
        DiagnosticSections.proxySections(
                details,
                sensitive,
                Collections.emptyList(),
                Collections.singletonList(lobby),
                Arrays.asList("lobby", "lobby", null),
                Arrays.asList(20L, -1L, 400L));
        return details;
    }
}
