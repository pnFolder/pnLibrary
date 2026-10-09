package ru.privatenull.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType

class PlatformAdapterLoggingTest {
    @Test
    fun `default log implementation forwards arguments to the configured handler`() {
        val owner = Any()
        val error = IllegalStateException("failure")
        var receivedOwner: Any? = null
        var receivedLevel: LogLevel? = null
        var receivedMessage: String? = null
        var receivedError: Throwable? = null
        val adapter = TestPlatformAdapter { actualOwner, level, message, actualError ->
            receivedOwner = actualOwner
            receivedLevel = level
            receivedMessage = message
            receivedError = actualError
        }

        adapter.log(owner, LogLevel.ERROR, "message", error)

        assertSame(owner, receivedOwner)
        assertEquals(LogLevel.ERROR, receivedLevel)
        assertEquals("message", receivedMessage)
        assertSame(error, receivedError)
    }

    private class TestPlatformAdapter(
        override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit,
    ) : PlatformAdapter {
        override val type = PlatformType.BUKKIT

        override fun executeGlobal(task: Runnable) = task.run()

        override fun executeReply(recipient: Any, task: Runnable) = task.run()

        override fun close() = Unit
    }
}
