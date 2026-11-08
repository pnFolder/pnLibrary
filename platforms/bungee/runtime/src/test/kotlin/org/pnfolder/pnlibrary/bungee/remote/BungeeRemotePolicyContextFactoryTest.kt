package org.pnfolder.pnlibrary.bungee.remote

import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.plugin.PluginDescription
import net.md_5.bungee.api.plugin.PluginManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.platform.PlatformType

/** Checks the remote-policy context built from BungeeCord metadata. */
class BungeeRemotePolicyContextFactoryTest {
    private val manager = mock(PluginManager::class.java)
    private val proxy = mock(ProxyServer::class.java).also {
        `when`(it.name).thenReturn("BungeeCord")
        `when`(it.version).thenReturn("1.21-test")
        `when`(it.pluginManager).thenReturn(manager)
    }
    private val plugin = mock(Plugin::class.java).also {
        `when`(it.proxy).thenReturn(proxy)
        `when`(it.description).thenReturn(PluginDescription().apply { name = "pnShop"; version = "2.0" })
    }

    @Test
    fun `the context describes the plugin and the proxy`() {
        val context = BungeeRemotePolicyContextFactory.create(plugin)

        assertEquals("pnshop", context.product.id)
        assertEquals("pnShop", context.product.name)
        assertEquals("2.0", context.product.version)
        assertEquals(PlatformType.BUNGEECORD, context.platform.type)
        assertEquals("BungeeCord", context.platform.name)
        assertEquals("1.21-test", context.platform.version)
    }

    @Test
    fun `given values are passed on and native handles are reachable`() {
        val context = BungeeRemotePolicyContextFactory.create(plugin, mapOf("channel" to "beta"))

        assertEquals("beta", context.values["channel"])
        assertSame(plugin, context.nativeHandle(Plugin::class.java))
        assertSame(proxy, context.nativeHandle(ProxyServer::class.java))
        assertSame(manager, context.nativeHandle(PluginManager::class.java))
    }
}
