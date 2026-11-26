package org.pnfolder.pnlibrary.velocity.diagnostics;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.meta.PluginDependency;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.spi.platform.DiagnosticDetailsCollector;
import org.pnfolder.pnlibrary.spi.platform.DiagnosticSections;
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot;
import org.pnfolder.pnlibrary.spi.platform.ProxyBackend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects the small set of Velocity facts that is useful during incident diagnosis. */
public final class VelocityDiagnosticDetailsCollector {
    private final ProxyServer server;

    /** Creates a collector that reads {@code server}. */
    public VelocityDiagnosticDetailsCollector(@NotNull ProxyServer server) {
        this.server = server;
    }

    /** Adds proxy plugins, backend servers and player pings to {@code snapshot}; sensitive values only when asked. */
    @NotNull
    public Map<String, Object> collect(@NotNull PlatformSnapshot snapshot, boolean includeSensitive) {
        return new DiagnosticDetailsCollector(() -> snapshot).collect(includeSensitive, details -> {
            Collection<PluginContainer> plugins = server.getPluginManager().getPlugins();
            List<Player> players = new ArrayList<>(server.getAllPlayers());
            DiagnosticSections.proxySections(
                    details,
                    includeSensitive,
                    DiagnosticSections.missingDependencies(requiredDependencies(plugins), installedIds(plugins)),
                    backends(),
                    playerBackends(players),
                    pings(players));
        });
    }

    private static Map<String, Collection<String>> requiredDependencies(Collection<PluginContainer> plugins) {
        Map<String, Collection<String>> required = new LinkedHashMap<>();
        for (PluginContainer container : plugins) {
            List<String> ids = new ArrayList<>();
            for (PluginDependency dependency : container.getDescription().getDependencies()) {
                if (!dependency.isOptional()) {
                    ids.add(dependency.getId());
                }
            }
            required.put(container.getDescription().getId(), ids);
        }
        return required;
    }

    private static Set<String> installedIds(Collection<PluginContainer> plugins) {
        Set<String> ids = new HashSet<>();
        for (PluginContainer container : plugins) {
            ids.add(container.getDescription().getId());
        }
        return ids;
    }

    private List<ProxyBackend> backends() {
        List<ProxyBackend> backends = new ArrayList<>();
        for (RegisteredServer connection : server.getAllServers()) {
            backends.add(new ProxyBackend(connection.getServerInfo().getName(),
                    connection.getPlayersConnected().size(), () -> connection.getServerInfo().getAddress()));
        }
        return backends;
    }

    private static List<String> playerBackends(List<Player> players) {
        List<String> names = new ArrayList<>();
        for (Player player : players) {
            ServerConnection connection = player.getCurrentServer().orElse(null);
            names.add(connection != null ? connection.getServerInfo().getName() : null);
        }
        return names;
    }

    private static List<Long> pings(List<Player> players) {
        List<Long> pings = new ArrayList<>();
        for (Player player : players) {
            pings.add(player.getPing());
        }
        return pings;
    }
}
