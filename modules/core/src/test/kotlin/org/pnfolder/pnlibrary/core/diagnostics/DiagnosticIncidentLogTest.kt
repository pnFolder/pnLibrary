package org.pnfolder.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticValueSanitizer

class DiagnosticIncidentLogTest {
    private fun log(capacity: Int) = DiagnosticIncidentLog(capacity, DiagnosticValueSanitizer())

    private fun DiagnosticIncidentLog.add(code: String, message: String = "m") =
        record("plugin", DiagnosticLevel.WARNING, "component", code, message, null, emptyMap())

    @Test
    fun `the same incident is counted, not repeated`() {
        val incidents = log(10)

        repeat(3) { incidents.add("A") }

        val snapshot = incidents.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals(3L, snapshot.single()["occurrenceCount"])
    }

    @Test
    fun `the oldest incident leaves when the log is full`() {
        val incidents = log(2)

        incidents.add("A")
        incidents.add("B")
        incidents.add("C")

        assertEquals(listOf("B", "C"), incidents.snapshot().map { it["code"] })
    }

    @Test
    fun `the timeline stops growing and the rest is counted as omitted`() {
        val incidents = log(10)

        repeat(600) { incidents.add("A") }

        val incident = incidents.snapshot().single()
        assertEquals(600L, incident["occurrenceCount"])
        assertEquals(500, (incident["occurrenceTimeline"] as List<*>).size)
        assertEquals(100L, incident["omittedOccurrences"])
    }

    @Test
    fun `a snapshot is a copy that later records do not change`() {
        val incidents = log(10)
        incidents.add("A")
        val before = incidents.snapshot()

        incidents.add("A")

        assertEquals(1L, before.single()["occurrenceCount"])
        assertEquals(1, (before.single()["occurrenceTimeline"] as List<*>).size)
    }

    @Test
    fun `the summary counts incidents and occurrences`() {
        val incidents = log(10)
        incidents.add("A")
        incidents.add("A")
        incidents.add("B")

        val summary = incidents.summary()

        assertEquals(2, summary["incidentCount"])
        assertEquals(3L, summary["occurrenceCount"])
    }
}
