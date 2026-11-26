package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeAnalytics;

class RuntimeAnalyticsTest {
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void criticalRuntimeExposesTaskAndUpdateSignals() {
        Map<String, Object> analytics = RuntimeAnalytics.of(3, 2, 1, 1, map("count", 4), map("occurrenceCount", 2, "incidentCount", 1),
            map("count", 5), map("created", 6, "failed", 1, "successRate", 0.5), map("errorCount", 2));

        assertEquals("critical", analytics.get("status"));
        assertEquals(3, ((List<?>) analytics.get("signals")).size());
        assertEquals(3, ((Map<?, ?>) analytics.get("resources")).get("registeredTasks"));
        assertEquals(2, ((Map<?, ?>) analytics.get("counts")).get("observabilityErrors"));
    }

    @Test
    void healthyRuntimeHasNoSignals() {
        Map<String, Object> none = Collections.emptyMap();
        Map<String, Object> analytics = RuntimeAnalytics.of(0, 0, 0, 0, none, none, none, none, none);

        assertEquals("healthy", analytics.get("status"));
        assertTrue(((List<?>) analytics.get("signals")).isEmpty());
    }
}
