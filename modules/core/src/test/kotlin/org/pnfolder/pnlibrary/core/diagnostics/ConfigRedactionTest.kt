package org.pnfolder.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

class ConfigRedactionTest {
    private fun redaction(
        excluded: Set<String> = emptySet(),
        keys: List<Pattern> = emptyList(),
        values: List<Pattern> = emptyList(),
    ) = ConfigRedaction(excluded, keys, values, DiagnosticRedactor())

    @Test
    fun `excluded path and secret key are hidden in requested and redact`() {
        val data = mapOf("db" to mapOf("pass" to "hunter2", "host" to "h"), "api" to "tok", "name" to "n")
        val redaction = redaction(setOf("db.pass"), ConfigRedaction.compile(listOf("^api$")))
        for (result in listOf(redaction.requested(data), redaction.redact(data))) {
            assertFalse(result.toString().contains("hunter2"), result.toString())
            assertFalse(result.toString().contains("tok"), result.toString())
            assertEquals("n", result["name"])
        }
    }

    @Test
    fun `value pattern replaces the match in requested values`() {
        val result = redaction(values = listOf(Pattern.compile("leak"))).requested(mapOf("note" to "a leak here"))
        assertFalse(result["note"].toString().contains("leak"))
    }
}
