package ru.privatenull.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DiagnosticSectionsTest {
    @Test
    fun `percentile uses the nearest rank so one slow player is not hidden`() {
        assertEquals(500L, DiagnosticSections.percentile(listOf(10L, 500L), 0.95))
        assertEquals(20L, DiagnosticSections.percentile(listOf(10L, 20L, 30L, 40L), 0.5))
        assertEquals(7L, DiagnosticSections.percentile(listOf(7L), 0.95))
        assertNull(DiagnosticSections.percentile(emptyList(), 0.95))
        assertThrows(IllegalArgumentException::class.java) { DiagnosticSections.percentile(listOf(1L), 1.5) }
    }

    @Test
    fun `ping summary reports sample count and statistics`() {
        val summary = DiagnosticSections.pingSummary(listOf(10L, 30L, 500L))
        assertEquals(3, summary["samples"])
        assertEquals(10L, summary["min"])
        assertEquals(500L, summary["max"])
        assertEquals(500L, summary["p95"])
        assertEquals(180.0, summary["average"])
        assertNull(DiagnosticSections.pingSummary(emptyList())["average"])
    }

    @Test
    fun `missing dependencies are reported per plugin in sorted order`() {
        val result = DiagnosticSections.missingDependencies(
            mapOf("A" to listOf("Vault", "ProtocolLib"), "B" to listOf("Vault"), "C" to emptyList()),
            installed = setOf("Vault", "A", "B", "C"),
        )
        assertEquals(listOf(mapOf("plugin" to "A", "missing" to listOf("ProtocolLib"))), result)
    }

    @Test
    fun `health is attention only when a signal exists`() {
        assertEquals("healthy", DiagnosticSections.health(emptyList())["status"])
        val signals = DiagnosticSections.commonSignals(
            missingDependencies = listOf(mapOf("plugin" to "A", "missing" to listOf("Vault"))),
            pings = listOf(400L),
        )
        assertEquals(listOf("missingRequiredDependencies", "highPlayerLatency"), signals.map { it["code"] })
        assertEquals("attention", DiagnosticSections.health(signals)["status"])
    }
}
