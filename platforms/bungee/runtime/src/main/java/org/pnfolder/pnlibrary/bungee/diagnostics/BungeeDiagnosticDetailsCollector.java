package org.pnfolder.pnlibrary.bungee.diagnostics;

import java.util.Objects;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Plugin;
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

/** Collects the Bungee facts that are useful during incident diagnosis. */
public final class BungeeDiagnosticDetailsCollector {
    private final Plugin plugin;

    /** Creates a collector that reads the proxy of {@code plugin}. */
    public BungeeDiagnosticDetailsCollector(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Adds plugins, backend servers and player pings to {@code snapshot}; sensitive values only when asked. */
    @NotNull
    public Map<String, Object> collect(@NotNull PlatformSnapshot snapshot, boolean includeSensitive) {
        return new DiagnosticDetailsCollector(() -> snapshot).collect(includeSensitive, details -> {
            ProxyServer proxy = plugin.getProxy();
            Collection<Plugin> plugins = proxy.getPluginManager().getPlugins();
            List<ProxiedPlayer> players = new ArrayList<>(proxy.getPlayers());
            DiagnosticSections.proxySections(
                    details,
                    includeSensitive,
                    DiagnosticSections.missingDependencies(requiredDependencies(plugins), installedNames(plugins)),
                    backends(proxy),
                    playerBackends(players),
                    pings(players));
        });
    }

    private static Map<String, Collection<String>> requiredDependencies(Collection<Plugin> plugins) {
        Map<String, Collection<String>> required = new LinkedHashMap<>();
        for (Plugin installed : plugins) {
            required.put(installed.getDescription().getName(), installed.getDescription().getDepends());
        }
        return required;
    }

    private static Set<String> installedNames(Collection<Plugin> plugins) {
        Set<String> names = new HashSet<>();
        for (Plugin installed : plugins) {
            names.add(installed.getDescription().getName());
        }
        return names;
    }

    // ServerInfo.getSocketAddress is missing on old proxies; getAddress stays supported there.
    @SuppressWarnings("deprecation")
    private static List<ProxyBackend> backends(ProxyServer proxy) {
        List<ProxyBackend> backends = new ArrayList<>();
        for (Map.Entry<String, ServerInfo> entry : proxy.getServers().entrySet()) {
            ServerInfo info = entry.getValue();
            backends.add(new ProxyBackend(entry.getKey(), info.getPlayers().size(), info::getAddress));
        }
        return backends;
    }

    private static List<String> playerBackends(List<ProxiedPlayer> players) {
        List<String> names = new ArrayList<>();
        for (ProxiedPlayer player : players) {
            Server server = player.getServer();
            names.add(server != null ? server.getInfo().getName() : null);
        }
        return names;
    }

    private static List<Long> pings(List<ProxiedPlayer> players) {
        List<Long> pings = new ArrayList<>();
        for (ProxiedPlayer player : players) {
            pings.add((long) player.getPing());
        }
        return pings;
    }
}
