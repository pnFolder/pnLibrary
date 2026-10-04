package ru.privatenull.pnlibrary.core.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.activity.ActivityCategory
import ru.privatenull.pnlibrary.api.activity.ActivityEvent
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import ru.privatenull.pnlibrary.api.observability.ObservationLevel
import ru.privatenull.pnlibrary.api.observability.ObservationRequest
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import java.nio.file.Files

class CompatibilityAdapterTest {
    @Test
    fun `new and activity entry points share one journal`() {
        val runtime = ObservabilityRuntime(Files.createTempDirectory("compatibility"))
        val service = UnifiedObservabilityService(DiagnosticsRegistry(), runtime)

        service.record(ObservationRequest(message = "new", level = ObservationLevel.NOTICE))
        service.record(ActivityEvent(type = "legacy", category = ActivityCategory.DIAGNOSTICS))

        assertEquals(listOf("new", "legacy"), runtime.recent().map { it.message })
    }

    @Test
    fun `one diagnostic call creates one observation`() {
        val runtime = ObservabilityRuntime(Files.createTempDirectory("diagnostic-compatibility"))
        val registry = DiagnosticsRegistry()
        val service = UnifiedObservabilityService(registry, runtime)
        registry.onActivityEvent { plugin, level, component, code, message, error, fields ->
            runtime.record(ObservationRequest(
                plugin = plugin,
                source = component,
                message = message,
                level = when (level) {
                    DiagnosticLevel.INFO -> ObservationLevel.INFO
                    DiagnosticLevel.WARNING -> ObservationLevel.WARNING
                    DiagnosticLevel.ERROR -> ObservationLevel.ERROR
                },
                data = fields.mapValues { it.value?.toString() ?: "null" } + ("code" to code),
                error = error,
            ))
        }

        service.record("acceptance", DiagnosticLevel.ERROR, "database", "TIMEOUT", "Timed out")

        assertEquals(1, runtime.recent().size)
        assertEquals("TIMEOUT", runtime.recent().single().data["code"])
    }

    @Test
    fun `legacy byte attachment keeps its declared metadata without a temporary file`() {
        val runtime = ObservabilityRuntime(Files.createTempDirectory("byte-attachment"))
        val service = UnifiedObservabilityService(DiagnosticsRegistry(), runtime)
        val bytes = "server output".toByteArray()

        val attachment = service.attach("event-1", "latest.log", "text/x-log", bytes)

        assertEquals("latest.log", attachment.name)
        assertEquals("text/x-log", attachment.contentType)
        assertArrayEquals(bytes, runtime.attachmentBytes(runtime.attachments("event-1").single()))
    }
}
