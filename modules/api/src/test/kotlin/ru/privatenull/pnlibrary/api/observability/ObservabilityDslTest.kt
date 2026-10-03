package ru.privatenull.pnlibrary.api.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class ObservabilityDslTest {
    @Test
    fun `capture describes context and files without technical identifiers`() {
        val log = Paths.get("logs/latest.log")
        val service = RecordingObservabilityService()

        service.capture {
            plugin("acceptance")
            source("Updater")
            message("Проверка обновления завершилась ошибкой")
            data("repository" to "pnFolder/pnLibrary")
            files(log)
        }

        val request = service.lastRequest
        assertEquals("acceptance", request.plugin)
        assertEquals("Updater", request.source)
        assertEquals("Проверка обновления завершилась ошибкой", request.message)
        assertEquals(mapOf("repository" to "pnFolder/pnLibrary"), request.data)
        assertEquals(listOf(log), request.files)
    }

    @Test
    fun `failure carries throwable without duplicating it in metadata`() {
        val error = IllegalStateException("GitHub timeout")
        val service = RecordingObservabilityService()

        service.failure(error) {
            plugin("acceptance")
            source("Updater")
        }

        assertSame(error, service.lastRequest.error)
        assertEquals(ObservationLevel.ERROR, service.lastRequest.level)
        assertEquals("GitHub timeout", service.lastRequest.message)
    }

    private class RecordingObservabilityService : ObservabilityService {
        lateinit var lastRequest: ObservationRequest

        override fun record(request: ObservationRequest): Observation {
            lastRequest = request
            return Observation.from(request)
        }

        override fun recent(query: ObservationQuery): List<Observation> = emptyList()

        override fun status(status: ComponentStatus): ComponentStatus = status

        override fun createReport(request: ObservabilityReportRequest): ObservabilityReport {
            throw UnsupportedOperationException()
        }

        override fun close() = Unit
    }
}
