package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticValueSanitizer;

class DiagnosticValueSanitizerTest {
    private final DiagnosticValueSanitizer sanitizer = new DiagnosticValueSanitizer();

    @Test
    void redactsSecretFieldsAndTerminatesRecursiveContainers() {
        Map<String, Object> recursive = new LinkedHashMap<String, Object>();
        recursive.put("self", recursive);
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("databasePassword", "do-not-leak");
        input.put("nested", recursive);

        Map<String, Object> result = sanitizer.map(input);

        assertEquals("[REDACTED]", result.get("databasePassword"));
        assertEquals(
            Collections.singletonMap("self", Collections.singletonMap("limit", "[recursive/depth limit]")),
            result.get("nested"));
    }

    @Test
    void incidentFingerprintIgnoresVolatileUuidsAndLongNumbers() {
        String first = sanitizer.incidentId("Example", DiagnosticLevel.ERROR, "storage", "write-failed",
            "Player 123456 failed: 123e4567-e89b-12d3-a456-426614174000", null);
        String equivalent = sanitizer.incidentId("example", DiagnosticLevel.ERROR, "storage", "write-failed",
            "Player 987654 failed: 987e6543-e21b-34d3-b654-123456789abc", null);
        String different = sanitizer.incidentId("example", DiagnosticLevel.ERROR, "storage", "read-failed",
            "Player 987654 failed: 987e6543-e21b-34d3-b654-123456789abc", null);

        assertEquals(first, equivalent);
        assertNotEquals(first, different);
    }
}
