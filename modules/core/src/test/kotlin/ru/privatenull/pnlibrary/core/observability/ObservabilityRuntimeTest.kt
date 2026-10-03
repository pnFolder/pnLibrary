package ru.privatenull.pnlibrary.core.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.observability.ComponentStatus
import ru.privatenull.pnlibrary.api.observability.ObservationLevel
import ru.privatenull.pnlibrary.api.observability.ObservationQuery
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ObservabilityRuntimeTest {
    @Test
    fun `capture stores one normalized observation and its files`() {
        val root = Files.createTempDirectory("observability-runtime")
        val log = root.resolve("latest.log").also { Files.writeString(it, "failure details") }
        val runtime = ObservabilityRuntime(root, clock = { 123L })

        val captured = runtime.capture {
            plugin("acceptance")
            source("startup")
            message("Plugin started")
            data("attempt" to 2)
            files(log)
        }

        assertEquals(123L, captured.timestamp)
        assertTrue(captured.files.isEmpty())
        assertEquals(listOf(captured), runtime.recent())
        assertEquals(1, runtime.attachments(captured.id).size)
    }

    @Test
    fun `failure is recorded once with error details`() {
        val root = Files.createTempDirectory("observability-failure")
        val runtime = ObservabilityRuntime(root)

        runtime.failure(IllegalStateException("database unavailable")) {
            plugin("acceptance")
            source("database")
        }

        val observations = runtime.recent()
        assertEquals(1, observations.size)
        assertEquals(ObservationLevel.ERROR, observations.single().level)
        assertEquals("java.lang.IllegalStateException", observations.single().errorType)
        assertEquals("database unavailable", observations.single().message)
    }

    @Test
    fun `status replaces the previous value for the same component`() {
        val runtime = ObservabilityRuntime(Files.createTempDirectory("observability-status"))

        runtime.status(ComponentStatus("acceptance", "database", "starting"))
        val ready = runtime.status(ComponentStatus("acceptance", "database", "ready"))

        assertEquals(listOf(ready), runtime.statuses())
    }

    @Test
    fun `concurrent captures are not lost or duplicated`() {
        val runtime = ObservabilityRuntime(Files.createTempDirectory("observability-concurrent"))
        val executor = Executors.newFixedThreadPool(8)

        repeat(100) { index ->
            executor.submit { runtime.capture { message("event-$index") } }
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        val observations = runtime.recent(ObservationQuery(limit = 200))
        assertEquals(100, observations.size)
        assertEquals(100, observations.map { it.id }.toSet().size)
    }
}
