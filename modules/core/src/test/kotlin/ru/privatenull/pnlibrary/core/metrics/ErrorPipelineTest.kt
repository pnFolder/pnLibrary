package ru.privatenull.pnlibrary.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.TelemetryError

class ErrorPipelineTest {
    @Test
    fun `sanitizes and deduplicates errors before forwarding`() {
        val received = mutableListOf<TelemetryError>()
        val delegate = object : ErrorReporter {
            override fun capture(error: TelemetryError) {
                received += error
            }

            override fun close() = Unit
        }
        val pipeline = ErrorPipeline(delegate)
        val error = TelemetryError(
            type = "java.lang.IllegalStateException",
            message = "Bearer secret-token?token=abc",
            stackTrace = listOf("C:\\Users\\alice\\plugin\\Main.kt:10"),
            handled = false,
        )

        pipeline.capture(error)
        pipeline.capture(error)

        assertEquals(1, received.size)
        assertEquals("Bearer [redacted]?token=[redacted]", received.single().message)
        assertEquals("C:\\Users\\[user]\\plugin\\Main.kt:10", received.single().stackTrace.single())
    }
}
