package org.pnfolder.pnlibrary.api.plugin

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.metrics.MetricsController

class MetricsControllerApiTest {
    @Test
    fun `exposes optional error reporter access`() {
        assertNotNull(MetricsController::class.java.getMethod("errorReporterOrNull"))
    }
}
