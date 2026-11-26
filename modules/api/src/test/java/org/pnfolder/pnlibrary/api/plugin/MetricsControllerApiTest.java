package org.pnfolder.pnlibrary.api.plugin;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.MetricsController;

class MetricsControllerApiTest {
    @Test
    void exposesOptionalErrorReporterAccess() throws NoSuchMethodException {
        assertNotNull(MetricsController.class.getMethod("errorReporterOrNull"));
    }
}
