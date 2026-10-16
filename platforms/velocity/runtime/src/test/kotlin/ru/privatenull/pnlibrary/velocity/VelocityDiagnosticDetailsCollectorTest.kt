package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot
import java.net.InetSocketAddress

class VelocityDiagnosticDetailsCollectorTest {
    @Test
    fun `p95 includes high latency in a small player population`() {
        val proxy = mock<ProxyServer>()
        whenever(proxy.pluginManager).thenReturn(mock())
        whenever(proxy.pluginManager.plugins).thenReturn(emptyList())
        whenever(proxy.allServers).thenReturn(emptyList())
        val players = listOf(10L, 500L).map { ping ->
            mock<com.velocitypowered.api.proxy.Player>().also {
                whenever(it.ping).thenReturn(ping)
                whenever(it.currentServer).thenReturn(java.util.Optional.empty())
            }
        }
        whenever(proxy.allPlayers).thenReturn(players)
        val report = VelocityDiagnosticDetailsCollector(proxy)
            .collect(PlatformSnapshot("Velocity", "test"), false)
        val summary = report["players"] as Map<*, *>
        assertEquals(500L, (summary["pingMs"] as Map<*, *>)["p95"])
        assertEquals("attention", (report["health"] as Map<*, *>)["status"])
    }

    @Test
    fun `backend address is exposed only in sensitive reports`() {
        val proxy = mock<ProxyServer>()
        val backend = mock<RegisteredServer>()
        whenever(proxy.pluginManager).thenReturn(mock())
        whenever(proxy.pluginManager.plugins).thenReturn(emptyList())
        whenever(proxy.allPlayers).thenReturn(emptyList())
        whenever(proxy.allServers).thenReturn(listOf(backend))
        whenever(backend.playersConnected).thenReturn(emptyList())
        whenever(backend.serverInfo).thenReturn(ServerInfo(
            "lobby", InetSocketAddress.createUnresolved("backend.internal", 25565),
        ))
        val collector = VelocityDiagnosticDetailsCollector(proxy)
        val snapshot = PlatformSnapshot("Velocity", "test")
        fun backend(sensitive: Boolean): Map<*, *> =
            (collector.collect(snapshot, sensitive)["backends"] as List<*>).single() as Map<*, *>

        assertFalse(backend(false).containsKey("host"))
        assertFalse(backend(false).containsKey("port"))
        assertEquals("backend.internal", backend(true)["host"])
        assertEquals(25565, backend(true)["port"])
        val players = collector.collect(snapshot, false)["players"] as Map<*, *>
        assertEquals(0, players["online"])
        assertNull((players["pingMs"] as Map<*, *>)["average"])
    }
}
