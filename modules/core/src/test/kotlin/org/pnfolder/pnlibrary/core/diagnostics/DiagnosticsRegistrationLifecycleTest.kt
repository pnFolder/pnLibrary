package org.pnfolder.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticLevel
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsContributor

class DiagnosticsRegistrationLifecycleTest {
    @Test
    fun `close is observable idempotent and removes contributor`() {
        val registry = DiagnosticsRegistry()
        val registration = registry.register("demo", DiagnosticsContributor { mapOf("ok" to true) })

        assertFalse(registration.isClosed)
        assertTrue(registry.snapshot("demo").toString().contains("ok"))

        registration.close()
        registration.close()

        assertTrue(registration.isClosed)
        assertFalse(registry.snapshot("demo").toString().contains("ok"))
    }

    @Test
    fun `closing an old registration keeps the same contributor registered again`() {
        val registry = DiagnosticsRegistry()
        val contributor = DiagnosticsContributor { mapOf("ok" to true) }
        val old = registry.register("demo", contributor)
        registry.register("demo", contributor)

        old.close()

        assertTrue(registry.snapshot("demo").toString().contains("ok"))
    }

    @Test
    fun `event snapshot does not change when the incident repeats later`() {
        val registry = DiagnosticsRegistry()
        registry.record("demo", DiagnosticLevel.WARNING, "db", "slow", "Slow query", null, emptyMap())
        @Suppress("UNCHECKED_CAST")
        val before = (registry.eventSnapshot()["demo"] as List<Map<String, Any?>>).single()

        registry.record("demo", DiagnosticLevel.WARNING, "db", "slow", "Slow query", null, emptyMap())

        assertEquals(1L, before["occurrenceCount"])
        assertEquals(1, (before["occurrenceTimeline"] as List<*>).size)
    }
}
