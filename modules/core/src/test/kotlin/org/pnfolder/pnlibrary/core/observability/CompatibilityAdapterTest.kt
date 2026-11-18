package org.pnfolder.pnlibrary.core.observability

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticLevel
import org.pnfolder.pnlibrary.api.observability.ObservationLevel
import org.pnfolder.pnlibrary.api.observability.ObservationRequest
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths
import java.nio.file.Files

class CompatibilityAdapterTest {
    @Test
    fun `observation records land in one journal in order`() {
        val runtime = ObservabilityRuntime(LibraryPaths(Files.createTempDirectory("compatibility")))
        val service = ObservabilityHub(DiagnosticsRegistry(), runtime)

        service.record(ObservationRequest(message = "new", level = ObservationLevel.NOTICE))
        service.record(ObservationRequest(message = "second"))

        assertEquals(listOf("new", "second"),runtime.recent().map { it.message })
    }

    @Test
    fun `one diagnostic call creates one observation`() {
        val runtime = ObservabilityRuntime(LibraryPaths(Files.createTempDirectory("diagnostic-compatibility")))
        val registry = DiagnosticsRegistry()
        val service = ObservabilityHub(registry, runtime)
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
    fun `byte attachment keeps its declared metadata without a temporary file`() {
        val runtime = ObservabilityRuntime(LibraryPaths(Files.createTempDirectory("byte-attachment")))
        val bytes = "server output".toByteArray()

        val attachment = runtime.attachBytes("event-1", "latest.log", "text/x-log", bytes)

        assertEquals("latest.log", attachment.originalName)
        assertEquals("text/x-log", attachment.contentType)
        assertArrayEquals(bytes, runtime.attachmentBytes(runtime.attachments("event-1").single()))
    }
}
