package ru.privatenull.pnlibrary.core.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.platform.PlatformType

class DiagnosticLogBufferTest {
    @Test
    fun `keeps only warning and error entries and redacts secrets`() {
        val buffer = DiagnosticLogBuffer(10)
        val platform = TestPlatform()
        buffer.record(platform, this, LogLevel.INFO, "ready", null)
        buffer.record(platform, this, LogLevel.WARNING, "token=https://example.invalid/private", null)

        val entries = buffer.snapshot()
        assertEquals(1, entries.size)
        assertEquals("WARNING", entries.single()["level"])
        val message = entries.single()["message"].toString()
        assertFalse(message.contains("example.invalid"))
        assertTrue(message.contains("REDACTED"))
    }

    @Test
    fun `aggregates repeats with exact timestamps and keeps the complete throwable`() {
        val buffer = DiagnosticLogBuffer(10)
        val platform = TestPlatform()
        val cause = NoSuchMethodError("PlayerPointsAPI.getCurrencyName(int)").apply {
            stackTrace = arrayOf(
                StackTraceElement("ru.privatenull.pncases.Menu", "open", "Menu.kt", 91),
                StackTraceElement("org.bukkit.plugin.EventExecutor", "execute", "EventExecutor.java", 77),
            )
        }
        val error = java.util.concurrent.CompletionException(cause).apply {
            stackTrace = arrayOf(
                StackTraceElement("java.util.concurrent.CompletableFuture", "encodeThrowable", "CompletableFuture.java", 315),
                StackTraceElement("ru.privatenull.pncases.AnvilGUI", "click", "AnvilGUI.java", 362),
            )
        }

        val first = buffer.record(platform, this, LogLevel.ERROR, "Could not pass event", error)
        val second = buffer.record(platform, this, LogLevel.ERROR, "Could not pass event", error)

        assertTrue(first?.emitOriginal == true)
        assertFalse(second?.emitOriginal == true)
        val incident = buffer.snapshot().single()
        val occurrences = incident["occurrences"] as Map<*, *>
        assertEquals(2L, occurrences["count"])
        assertEquals(2, (occurrences["timeline"] as List<*>).size)
        assertEquals("Menu.kt", (incident["origin"] as Map<*, *>)["file"])
        val fullError = incident["fullError"].toString()
        assertTrue(fullError.contains("CompletionException"))
        assertTrue(fullError.contains("NoSuchMethodError"))
        assertTrue(fullError.contains("CompletableFuture.java:315"))
        assertTrue(fullError.contains("Menu.kt:91"))
        assertTrue(fullError.contains("Caused by:"))
        val consoleBlock = incident["consoleBlock"].toString()
        assertTrue(consoleBlock.contains("ERROR] Could not pass event"))
        assertTrue(consoleBlock.contains("NoSuchMethodError"))
    }

    @Test
    fun `logger prints the full repeated error only once`() {
        val platform = TestPlatform()
        val logger = PlatformLoggingService(platform, DiagnosticLogBuffer(10)).logger(this, "test")
        val error = IllegalStateException("database unavailable")

        logger.error("Load failed", error)
        logger.error("Load failed", error)

        assertEquals(1, platform.logged.size)
        assertEquals(error, platform.logged.single().second)
    }

    private class TestPlatform : PlatformAdapter {
        val logged = mutableListOf<Pair<String, Throwable?>>()
        override val type = PlatformType.BUKKIT
        override val id = "test"
        override fun ownerDetails(owner: Any) = mapOf("name" to "test-plugin")
        override fun details(): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
            logged += message to error
        }
        override fun close() = Unit
    }
}
