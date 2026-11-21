package org.pnfolder.pnlibrary.velocity.remote

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.PluginDescription
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.util.ProxyVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.util.Optional

/** Checks the remote-policy context built from Velocity metadata. */
class VelocityRemotePolicyContextFactoryTest {
    private val server = mock<ProxyServer>().also {
        whenever(it.version).thenReturn(ProxyVersion("Velocity", "Velocity Team", "3.4-test"))
        whenever(it.pluginManager).thenReturn(mock())
    }

    private fun container(name: Optional<String>, version: Optional<String>): PluginContainer {
        val description = mock<PluginDescription>()
        whenever(description.id).thenReturn("pnshop")
        whenever(description.name).thenReturn(name)
        whenever(description.version).thenReturn(version)
        return mock<PluginContainer>().also { whenever(it.description).thenReturn(description) }
    }

    @Test
    fun `the context describes the plugin and the proxy`() {
        val context = VelocityRemotePolicyContextFactory.create(
            Any(), container(Optional.of("pnShop"), Optional.of("2.0")), server,
        )

        assertEquals("pnshop", context.product.id)
        assertEquals("pnShop", context.product.name)
        assertEquals("2.0", context.product.version)
        assertEquals(PlatformType.VELOCITY, context.platform.type)
        assertEquals("Velocity", context.platform.name)
        assertEquals("3.4-test", context.platform.version)
    }

    @Test
    fun `a plugin without a name or version still has a product`() {
        val context = VelocityRemotePolicyContextFactory.create(
            Any(), container(Optional.empty(), Optional.empty()), server,
        )

        assertEquals("pnshop", context.product.name)
        assertEquals("unknown", context.product.version)
    }

    @Test
    fun `given values are passed on and native handles are reachable`() {
        val plugin = Any()
        val context = VelocityRemotePolicyContextFactory.create(
            plugin, container(Optional.empty(), Optional.empty()), server, mapOf("channel" to "beta"),
        )

        assertEquals("beta", context.values["channel"])
        assertSame(plugin, context.nativeHandle(Any::class.java))
        assertSame(server, context.nativeHandle(ProxyServer::class.java))
    }
}
