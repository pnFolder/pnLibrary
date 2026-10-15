package ru.privatenull.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.logging.LogLevel

class UncaughtExceptionLogObserverTest {
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
