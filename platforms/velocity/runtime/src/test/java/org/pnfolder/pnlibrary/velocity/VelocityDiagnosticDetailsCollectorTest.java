package org.pnfolder.pnlibrary.velocity;

import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot;
import org.pnfolder.pnlibrary.velocity.diagnostics.VelocityDiagnosticDetailsCollector;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks the Velocity facts collected for diagnostics. */
class VelocityDiagnosticDetailsCollectorTest {

    @Test
    void p95IncludesHighLatencyInASmallPlayerPopulation() {
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager manager = mock(PluginManager.class);
        when(proxy.getPluginManager()).thenReturn(manager);
        when(manager.getPlugins()).thenReturn(Collections.emptyList());
        when(proxy.getAllServers()).thenReturn(Collections.<RegisteredServer>emptyList());
        List<Player> players = new ArrayList<>();
        for (long ping : new long[] {10L, 500L}) {
            Player player = mock(Player.class);
            when(player.getPing()).thenReturn(ping);
            when(player.getCurrentServer()).thenReturn(Optional.empty());
            players.add(player);
        }
        when(proxy.getAllPlayers()).thenReturn(players);

        Map<String, Object> report = new VelocityDiagnosticDetailsCollector(proxy)
                .collect(new PlatformSnapshot("Velocity", "test"), false);

        Map<?, ?> summary = (Map<?, ?>) report.get("players");
        assertEquals(500L, ((Map<?, ?>) summary.get("pingMs")).get("p95"));
        assertEquals("attention", ((Map<?, ?>) report.get("health")).get("status"));
    }

    @Test
    void backendAddressIsExposedOnlyInSensitiveReports() {
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager manager = mock(PluginManager.class);
        RegisteredServer backend = mock(RegisteredServer.class);
        when(proxy.getPluginManager()).thenReturn(manager);
        when(manager.getPlugins()).thenReturn(Collections.emptyList());
        when(proxy.getAllPlayers()).thenReturn(Collections.<Player>emptyList());
        when(proxy.getAllServers()).thenReturn(Collections.singletonList(backend));
        when(backend.getPlayersConnected()).thenReturn(Collections.<Player>emptyList());
        when(backend.getServerInfo()).thenReturn(
                new ServerInfo("lobby", InetSocketAddress.createUnresolved("backend.internal", 25565)));
        VelocityDiagnosticDetailsCollector collector = new VelocityDiagnosticDetailsCollector(proxy);
        PlatformSnapshot snapshot = new PlatformSnapshot("Velocity", "test");

        Map<?, ?> hidden = backendOf(collector, snapshot, false);
        Map<?, ?> shown = backendOf(collector, snapshot, true);

        assertFalse(hidden.containsKey("host"));
        assertFalse(hidden.containsKey("port"));
        assertEquals("backend.internal", shown.get("host"));
        assertEquals(25565, shown.get("port"));
        Map<?, ?> players = (Map<?, ?>) collector.collect(snapshot, false).get("players");
        assertEquals(0, players.get("online"));
        assertNull(((Map<?, ?>) players.get("pingMs")).get("average"));
    }

    private static Map<?, ?> backendOf(VelocityDiagnosticDetailsCollector collector, PlatformSnapshot snapshot,
                                       boolean sensitive) {
        List<?> backends = (List<?>) collector.collect(snapshot, sensitive).get("backends");
        assertEquals(1, backends.size());
        return (Map<?, ?>) backends.get(0);
    }
}
