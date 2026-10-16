package ru.privatenull.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.logging.LogLevel

class UncaughtExceptionLogObserverTest {
    @Test
    fun `observer failure cannot prevent the previous handler from seeing the exception`() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var received: Throwable? = null
        val failure = IllegalStateException("original")
        val bridge = UncaughtExceptionLogObserver(Any())
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, error -> received = error }
            bridge.observe { _, _, _, _ -> error("observer failed") }
            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), failure)
            }
            assertSame(failure, received)
        } finally {
            bridge.observe(null)
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun `replacing observer uses the latest callback`() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        val bridge = UncaughtExceptionLogObserver(Any())
        var oldCalls = 0
        var newCalls = 0
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            bridge.observe { _, _, _, _ -> oldCalls++ }
            bridge.observe { _, _, _, _ -> newCalls++ }
            Thread.getDefaultUncaughtExceptionHandler()
                .uncaughtException(Thread.currentThread(), IllegalStateException("test"))
            assertEquals(0, oldCalls)
            assertEquals(1, newCalls)
        } finally {
            bridge.observe(null)
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun `restores previous handler and reports owner`() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        val previous = Thread.UncaughtExceptionHandler { _, _ -> }
        val owner = Any()
        val error = IllegalStateException("boom")
        var receivedOwner: Any? = null
        var receivedLevel: LogLevel? = null
        var receivedError: Throwable? = null

        try {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            val observer = UncaughtExceptionLogObserver(owner)
            observer.observe { actualOwner, level, _, actualError ->
                receivedOwner = actualOwner
                receivedLevel = level
                receivedError = actualError
            }

            val installed = Thread.getDefaultUncaughtExceptionHandler()
            check(installed != null && installed !== previous)
            installed.uncaughtException(Thread.currentThread(), error)

            observer.observe(null)
            assertSame(previous, Thread.getDefaultUncaughtExceptionHandler())
            assertSame(owner, receivedOwner)
            assertEquals(LogLevel.ERROR, receivedLevel)
            assertSame(error, receivedError)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
