package org.pnfolder.pnlibrary.api.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugRequestTest {
    @Test
    void aPlainRequestAlwaysCarriesLogsAndConfigurations() {
        DebugRequest request = DebugRequest.parse(new String[] {"debug"}, true);

        assertEquals("all", request.getTarget());
        assertTrue(request.isLogs());
        assertTrue(request.isConfigs());
        assertFalse(request.isLocal());
    }

    @Test
    void localOnlyChangesWhereTheFileGoes() {
        DebugRequest request = DebugRequest.parse(new String[] {"pnClans", "--local"});

        assertEquals("pnClans", request.getTarget());
        assertTrue(request.isLocal() && request.isLogs() && request.isConfigs());
    }

    @Test
    void flagsForTheContentNoLongerExist() {
        for (String flag : new String[] {"--full", "--logs", "--config", "--no-logs", "--no-config", "--nope"}) {
            assertThrows(IllegalArgumentException.class, () -> DebugRequest.parse(new String[] {flag}), flag);
        }
        assertThrows(IllegalArgumentException.class, () -> DebugRequest.parse(new String[] {"--local", "--local"}));
        assertThrows(IllegalArgumentException.class, () -> DebugRequest.parse(new String[] {"a", "b"}));
    }
}
