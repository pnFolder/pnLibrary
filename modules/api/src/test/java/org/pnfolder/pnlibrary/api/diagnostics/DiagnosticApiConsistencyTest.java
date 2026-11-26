package org.pnfolder.pnlibrary.api.diagnostics;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticApiConsistencyTest {
    @Test
    void builtDeclarationsAndCollectedSnapshotsAreImmutable() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("state", "ready");
        DiagnosticConfiguration configuration = DiagnosticConfiguration.builder("config.yml")
                .exclude("database.password")
                .build();
        DiagnosticContainer container = DiagnosticContainer.builder("example")
                .snapshot(() -> source)
                .configuration(configuration)
                .build();

        source.put("state", "changed");
        Map<String, Object> snapshot = container.collect();
        source.put("later", true);

        assertEquals("changed", snapshot.get("state"));
        assertNull(snapshot.get("later"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("other", true));
        assertThrows(UnsupportedOperationException.class, () -> configuration.getExcludedPaths().add("token"));
    }
}
