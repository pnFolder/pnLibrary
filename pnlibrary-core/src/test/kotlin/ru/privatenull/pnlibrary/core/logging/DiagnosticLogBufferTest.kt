package ru.privatenull.pnlibrary.core.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.platform.PlatformVariant

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

    private class TestPlatform : PlatformAdapter {
        override val variant = PlatformVariant.BUKKIT
        override val id = "test"
        override fun ownerDetails(owner: Any) = mapOf("name" to "test-plugin")
        override fun details(): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
