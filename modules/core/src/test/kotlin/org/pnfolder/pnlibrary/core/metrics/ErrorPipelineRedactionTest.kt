package org.pnfolder.pnlibrary.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.metrics.ErrorReporter
import org.pnfolder.pnlibrary.api.metrics.TelemetryError
import java.util.UUID

class ErrorPipelineRedactionTest {
    /** The four replacements exactly as they were written inline before the patterns became constants. */
    private fun legacy(value: String?): String? = value
        ?.replace(Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+"), "$1[redacted]")
        ?.replace(Regex("(?i)([?&](?:api_?key|token|secret)=)[^&\\s]+"), "$1[redacted]")
        ?.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "[uuid]")
        ?.replace(Regex("(?i)(/home/|/Users/|C:\\\\Users\\\\)[^/\\\\\\s]+"), "$1[user]")

    @Test
    fun `sanitized text equals the old inline regex result on a fixed corpus`() {
        val corpus = listOf(
            "Bearer abc.def-123 failed",
            "GET /x?api_key=SECRET&token=t0k&other=1",
            "player ${UUID(1, 2)} and ${UUID(3, 4).toString().uppercase()}",
            "C:\\Users\\alice\\plugin\\Main.kt:10",
            "/home/bob/server/plugins/a.jar and /Users/carol/x",
            "nothing to hide",
            "",
        )
        val received = mutableListOf<TelemetryError>()
        val pipeline = ErrorPipeline(object : ErrorReporter {
            override fun capture(error: TelemetryError) { received += error }
            override fun close() = Unit
        })

        corpus.forEachIndexed { index, text ->
            pipeline.capture(TelemetryError("T$index", text, listOf(text), handled = true, attributes = mapOf("k" to text)))
        }

        assertEquals(corpus.size, received.size)
        corpus.forEachIndexed { index, text ->
            assertEquals(legacy(text), received[index].message)
            assertEquals(listOf(legacy(text)), received[index].stackTrace)
            assertEquals(legacy(text), received[index].attributes["k"])
        }
    }
}
