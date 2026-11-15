package org.pnfolder.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.logging.LogLevel
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class JulLogObserverTest {
    private class Seen(val owner: Any, val level: LogLevel, val message: String, val error: Throwable?)

    private val platform: Logger = Logger.getLogger("platform-" + UUID.randomUUID()).apply { useParentHandlers = false }
    private val fallback = Any()
    private val plugin = Any()
    private val seen = mutableListOf<Seen>()
    private val observer = JulLogObserver({ platform }, fallback) { name -> if (name == "Plugin") plugin else null }

    private fun start() = observer.observe { owner, level, message, error -> seen += Seen(owner, level, message, error) }

    @Test
    fun `warnings and errors from child loggers are forwarded with plugin attribution`() {
        start()
        val child = Logger.getLogger(platform.name + ".child").apply { parent = platform }
        val pluginLogger = Logger.getLogger("Plugin").apply { parent = platform; useParentHandlers = true }
        val failure = IllegalStateException("boom")

        child.warning("careful")
        pluginLogger.log(Level.SEVERE, "failed", failure)
        pluginLogger.info("ignored")

        assertEquals(listOf(LogLevel.WARNING, LogLevel.ERROR), seen.map { it.level })
        assertEquals(fallback, seen[0].owner)
        assertEquals(plugin, seen[1].owner)
        assertEquals(failure, seen[1].error)
    }

    @Test
    fun `own log calls are not reported back`() {
        start()
        observer.runOwn { platform.severe("internal") }
        platform.severe("external")
        assertEquals(listOf("external"), seen.map { it.message })
    }

    @Test
    fun `stopping removes the handler and repeated starts attach only one`() {
        start()
        start()
        platform.warning("once")
        assertEquals(1, seen.size)

        observer.observe(null)
        platform.warning("after stop")
        assertEquals(1, seen.size)
    }

    @Test
    fun `name matching prefers exact then longest contained name`() {
        val names = listOf("Vault", "Vault-Economy", "ab")
        assertEquals("Vault", JulLogObserver.matchName("vault", names))
        assertEquals("Vault-Economy", JulLogObserver.matchName("[Vault-Economy] sql", names))
        assertNull(JulLogObserver.matchName("about", names))
        assertNull(JulLogObserver.matchName(null, names))
    }

    @Test
    fun `plugin matching returns the plugin object with the identifying name`() {
        val plugins = listOf("Vault" to 1, "Vault-Economy" to 2)
        assertEquals(plugins[1], JulLogObserver.matchPlugin("[Vault-Economy] sql", plugins) { it.first })
        assertNull(JulLogObserver.matchPlugin("Other", plugins) { it.first })
    }

    @Test
    fun `log handler writes to the owner logger at the mapped level without observing it back`() {
        start()
        val records = mutableListOf<LogRecord>()
        val ownerLogger = Logger.getLogger(platform.name + ".owner").apply {
            parent = platform
            addHandler(object : Handler() {
                override fun publish(record: LogRecord) { records += record }
                override fun flush() = Unit
                override fun close() = Unit
            })
        }
        val failure = IllegalStateException("boom")
        val handler = observer.logHandler { owner -> if (owner === plugin) ownerLogger else platform }

        handler(plugin, LogLevel.ERROR, "failed", failure)
        handler(plugin, LogLevel.WARNING, "careful", null)
        handler(plugin, LogLevel.SUCCESS, "done", null)

        assertEquals(listOf(Level.SEVERE, Level.WARNING, Level.INFO), records.map { it.level })
        assertEquals(failure, records[0].thrown)
        assertTrue(seen.isEmpty())
    }
}
