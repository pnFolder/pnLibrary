package ru.privatenull.pnlibrary.velocity

import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.logging.LogLevel

class VelocityLogObserverTest {
    private class Seen(val owner: Any, val level: LogLevel, val message: String, val error: Throwable?)

    private class PluginA
    private class PluginB

    private val fallback = Any()
    private val pluginA = PluginA()
    private val pluginB = PluginB()
    private val seen = mutableListOf<Seen>()
    private val observer = VelocityLogObserver(fallback) { listOf(pluginA, pluginB) }

    private val loggerName = "ru.privatenull.pnlibrary.velocity.test.${System.nanoTime()}"

    private fun start() = observer.observe { owner, level, message, error -> seen += Seen(owner, level, message, error) }

    @Test
    fun `errors are forwarded and own log calls are skipped`() {
        start()
        val logger = LogManager.getLogger(loggerName)
        val failure = IllegalStateException("boom")

        logger.error("failed", failure)
        observer.runOwn { logger.error("internal") }
        logger.info("quiet")

        assertEquals(listOf("failed"), seen.map { it.message })
        assertEquals(LogLevel.ERROR, seen.single().level)
        assertSame(failure, seen.single().error)
        observer.observe(null)
    }

    @Test
    fun `stopping detaches the appender`() {
        start()
        observer.observe(null)
        LogManager.getLogger(loggerName).error("after stop")
        assertEquals(emptyList<String>(), seen.map { it.message })
    }

    @Test
    fun `logger names resolve to the plugin with the longest matching package`() {
        val pkg = PluginA::class.java.name.substringBeforeLast('.')
        assertSame(pluginA, observer.resolveOwner(PluginA::class.java.name))
        assertSame(pluginA, observer.resolveOwner("$pkg.storage.Pool"))
        assertNull(observer.resolveOwner("com.velocitypowered.proxy.Velocity"))
        assertNull(observer.resolveOwner(null))
    }
}
