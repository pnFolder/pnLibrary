package org.pnfolder.pnlibrary.core.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership

class DiagnosticCaptureTest {
    private val ownership = object : PlatformOwnership {
        override fun ownerDetails(owner: Any) = mapOf("name" to "plugin")
    }

    @Test
    fun `native echoes do not duplicate journal incidents or error observations`() {
        val buffer = DiagnosticLogBuffer()
        var journaled = 0
        var observed = 0
        var printed = 0
        lateinit var capture: DiagnosticCapture
        val native = object : PlatformLogging {
            override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
                printed++
                capture.observe(owner, level, message, error)
            }
        }
        capture = DiagnosticCapture(ownership, native, buffer, { _, _, _, _ -> journaled++ }) { _, _, _ -> observed++ }
        val error = IllegalStateException("failure")
        repeat(2) { capture.log(this, LogLevel.ERROR, "command failed", error) }
        assertEquals(1, printed)
        assertEquals(2, journaled)
        assertEquals(2, observed)
        assertEquals(2L, buffer.summary()["occurrenceCount"])

        capture.observeWithoutJournal(this, LogLevel.ERROR, "foreign failure", error)
        assertEquals(1, printed)
        assertEquals(2, journaled)
        assertEquals(3, observed)
        assertEquals(3L, buffer.summary()["occurrenceCount"])
    }

    @Test
    fun `a line written without record reaches the console only`() {
        val buffer = DiagnosticLogBuffer()
        var journaled = 0
        var observed = 0
        var printed = 0
        val native = object : PlatformLogging {
            override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) { printed++ }
        }
        val capture = DiagnosticCapture(ownership, native, buffer, { _, _, _, _ -> journaled++ }) { _, _, _ -> observed++ }
        val error = IllegalStateException("failure")

        capture.write(this, LogLevel.ERROR, "noisy but harmless", error, false)
        assertEquals(1, printed)
        assertEquals(0, journaled)
        assertEquals(0, observed)
        assertEquals(0L, buffer.summary()["occurrenceCount"])

        capture.write(this, LogLevel.ERROR, "critical", error)
        assertEquals(2, printed)
        assertEquals(1, journaled)
        assertEquals(1, observed)
        assertEquals(1L, buffer.summary()["occurrenceCount"])
    }

    @Test
    fun `a debug line is kept in the black box and never printed`() {
        var journaled = 0
        var printed = 0
        val native = object : PlatformLogging {
            override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) { printed++ }
        }
        val capture = DiagnosticCapture(ownership, native, DiagnosticLogBuffer(), { _, _, _, _ -> journaled++ })

        capture.write(this, LogLevel.DEBUG, "detail", null)
        capture.write(this, LogLevel.DEBUG, "skipped detail", null, false)

        assertEquals(0, printed)
        assertEquals(1, journaled)
    }

    @Test
    fun `an echo delivered on another thread is ignored but unrelated lines still count`() {
        var journaled = 0
        lateinit var capture: DiagnosticCapture
        val native = object : PlatformLogging {
            override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
                Thread { capture.observe(owner, level, message, error) }.apply { start() }.join()
            }
        }
        capture = DiagnosticCapture(ownership, native, DiagnosticLogBuffer(), { _, _, _, _ -> journaled++ })
        capture.log(this, LogLevel.WARNING, "disk is slow", null)
        assertEquals(1, journaled)

        Thread { capture.observe(this, LogLevel.WARNING, "someone else's warning", null) }.apply { start() }.join()
        assertEquals(2, journaled)
    }

    @Test
    fun `broken diagnostic sinks cannot prevent console output`() {
        var printed = 0
        val native = object : PlatformLogging {
            override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) { printed++ }
        }
        val capture = DiagnosticCapture(ownership, native, journal = { _, _, _, _ -> error("journal") }) { _, _, _ -> error("metrics") }
        capture.log(this, LogLevel.ERROR, "failure", IllegalStateException())
        assertEquals(1, printed)
    }
}
