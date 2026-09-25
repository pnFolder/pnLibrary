package ru.privatenull.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsContributor

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
}
