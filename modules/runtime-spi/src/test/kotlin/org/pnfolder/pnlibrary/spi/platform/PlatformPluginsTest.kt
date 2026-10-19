package org.pnfolder.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pnfolder.pnlibrary.api.logging.LogLevel

class PlatformPluginsTest {
    private class NativePlugin(val name: String, val version: String)

    private val alpha = NativePlugin("Alpha", "1.0")
    private val beta = NativePlugin("Beta", "2.1")
    private val installed = mutableListOf(alpha, beta)
    private val plugins = PlatformPlugins(
        platformName = "Test",
        installed = { installed },
        resolve = { owner -> owner as? NativePlugin },
        describe = { PluginSnapshot(id = it.name, name = it.name, version = it.version) },
    )

    @Test
    fun `snapshots and versions describe every installed plugin`() {
        assertEquals(listOf("Alpha", "Beta"), plugins.snapshots().map { it.id })
        assertEquals(mapOf("Alpha" to "1.0", "Beta" to "2.1"), plugins.versions())
    }

    @Test
    fun `metadata of an unsupported owner is logged once and null`() {
        val logged = mutableListOf<LogLevel>()
        val logging = object : PlatformLogging {
            override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit = { _, level, _, _ -> logged += level }
        }

        assertEquals("2.1", plugins.metadata(beta, logging)?.version)
        assertNull(plugins.metadata("not a plugin", logging))
        assertEquals(listOf(LogLevel.WARNING), logged)
    }

    @Test
    fun `required owner must be a native plugin that is installed`() {
        assertSame(alpha, plugins.requireInstalled(alpha, "Remote policy owner"))

        val foreign = assertThrows<IllegalArgumentException> { plugins.requireInstalled("text", "Remote policy owner") }
        assertEquals("Remote policy owner must be a Test plugin; received java.lang.String", foreign.message)

        installed -= alpha
        val removed = assertThrows<IllegalArgumentException> { plugins.requireInstalled(alpha, "Disabled owner") }
        assertTrue(removed.message!!.startsWith("Disabled owner is not installed on Test"))
    }
}
