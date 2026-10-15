package ru.privatenull.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticDetailsCollectorTest {
    @Test
    fun `collector creates common diagnostic envelope and preserves platform details`() {
        val snapshot = PlatformSnapshot(
            name = "Paper",
            version = "1.21.1",
        )

        val details = DiagnosticDetailsCollector(snapshotProvider = { snapshot })
            .collect(includeSensitive = false) {
                it["analytics"] = linkedMapOf("status" to "healthy")
            }

        assertEquals(snapshot.asMap(), details.filterKeys { it !in setOf("privacy", "analytics") })
        assertEquals(false, (details["privacy"] as Map<*, *>) ["sensitiveDataIncluded"])
        assertTrue((details["privacy"] as Map<*, *>) ["omittedFields"] is List<*>)
        assertEquals("healthy", (details["analytics"] as Map<*, *>) ["status"])
    }
}
