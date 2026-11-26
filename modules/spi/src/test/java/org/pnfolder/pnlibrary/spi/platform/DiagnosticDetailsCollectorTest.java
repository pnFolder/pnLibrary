package org.pnfolder.pnlibrary.spi.platform;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticDetailsCollectorTest {
    @Test
    void collectorCreatesCommonDiagnosticEnvelopeAndPreservesPlatformDetails() {
        PlatformSnapshot snapshot = new PlatformSnapshot("Paper", "1.21.1");

        Map<String, Object> details = new DiagnosticDetailsCollector(() -> snapshot)
                .collect(false, map -> {
                    Map<String, Object> analytics = new LinkedHashMap<>();
                    analytics.put("status", "healthy");
                    map.put("analytics", analytics);
                });

        Map<String, Object> common = new LinkedHashMap<>(details);
        common.remove("privacy");
        common.remove("analytics");
        assertEquals(snapshot.asMap(), common);
        Map<?, ?> privacy = (Map<?, ?>) details.get("privacy");
        assertEquals(false, privacy.get("sensitiveDataIncluded"));
        assertTrue(privacy.get("omittedFields") instanceof java.util.List);
        assertEquals("healthy", ((Map<?, ?>) details.get("analytics")).get("status"));
    }
}
