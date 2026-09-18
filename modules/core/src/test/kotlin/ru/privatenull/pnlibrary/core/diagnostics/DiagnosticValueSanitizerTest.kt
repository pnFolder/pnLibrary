package ru.privatenull.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel

class DiagnosticValueSanitizerTest {
    private val sanitizer = DiagnosticValueSanitizer()

    @Test
    fun `redacts secret fields and terminates recursive containers`() {
        val recursive = linkedMapOf<String, Any?>()
        recursive["self"] = recursive

        val result = sanitizer.map(
            mapOf(
                "databasePassword" to "do-not-leak",
                "nested" to recursive,
            ),
        )

        assertEquals("[REDACTED]", result["databasePassword"])
        assertEquals(
            mapOf("self" to mapOf("limit" to "[recursive/depth limit]")),
            result["nested"],
        )
    }

    @Test
    fun `incident fingerprint ignores volatile UUIDs and long numbers`() {
        val first = sanitizer.incidentId(
            plugin = "Example",
            level = DiagnosticLevel.ERROR,
            component = "storage",
            code = "write-failed",
            message = "Player 123456 failed: 123e4567-e89b-12d3-a456-426614174000",
            error = null,
        )
        val equivalent = sanitizer.incidentId(
            plugin = "example",
            level = DiagnosticLevel.ERROR,
            component = "storage",
            code = "write-failed",
            message = "Player 987654 failed: 987e6543-e21b-34d3-b654-123456789abc",
            error = null,
        )
        val different = sanitizer.incidentId(
            plugin = "example",
            level = DiagnosticLevel.ERROR,
            component = "storage",
            code = "read-failed",
            message = "Player 987654 failed: 987e6543-e21b-34d3-b654-123456789abc",
            error = null,
        )

        assertEquals(first, equivalent)
        assertNotEquals(first, different)
    }
}
