package org.pnfolder.pnlibrary.bungee.network;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.NetworkPlayer;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.core.menu.OwnerInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@link NetworkProxy} backed by the BungeeCord proxy. */
public final class BungeeNetworkProxy implements NetworkProxy {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final ProxyServer proxy;

    /** Wraps the native {@code proxy}. */
    public BungeeNetworkProxy(@NotNull ProxyServer proxy) {
        this.proxy = proxy;
    }

    @NotNull
    @Override
    public Iterable<? extends Audience> audiences() {
        return getPlayers();
    }

    @NotNull
    @Override
    public String getName() {
        return proxy.getName();
    }

    @NotNull
    @Override
    public String getVersion() {
        return proxy.getVersion();
    }

    @Override
    public int getPlayerCount() {
        return proxy.getOnlineCount();
    }

    @NotNull
    @Override
    public List<NetworkPlayer> getPlayers() {
        List<NetworkPlayer> players = new ArrayList<>();
        for (ProxiedPlayer player : proxy.getPlayers()) {
            players.add(new BungeeNetworkPlayer(player));
        }
        return players;
    }

    @NotNull
    @Override
    public List<NetworkServer> getServers() {
        List<NetworkServer> servers = new ArrayList<>();
        for (ServerInfo server : proxy.getServers().values()) {
            servers.add(new BungeeNetworkServer(server));
        }
        return servers;
    }

    @Nullable
    @Override
    public NetworkPlayer player(@NotNull String name) {
        ProxiedPlayer player = proxy.getPlayer(name);
        return player != null ? new BungeeNetworkPlayer(player) : null;
    }

    @Nullable
    @Override
    public NetworkPlayer player(@NotNull UUID uuid) {
        ProxiedPlayer player = proxy.getPlayer(uuid);
        return player != null ? new BungeeNetworkPlayer(player) : null;
    }

    @Nullable
    @Override
    public NetworkServer server(@NotNull String name) {
        for (Map.Entry<String, ServerInfo> entry : proxy.getServers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return new BungeeNetworkServer(entry.getValue());
            }
        }
        return null;
    }

    @Override
    public void registerChannel(@NotNull String channel) {
        proxy.registerChannel(channel);
    }

    @Override
    public void unregisterChannel(@NotNull String channel) {
        proxy.unregisterChannel(channel);
    }

    @Override
    public void stop(@Nullable Component reason) {
        if (reason != null) {
            proxy.stop(LEGACY.serialize(reason));
        } else {
            proxy.stop();
        }
    }

    /** Returns the pnLibrary owner info of a Bungee plugin instance, or null when {@code instance} is not one. */
    @Nullable
    public OwnerInfo describePlugin(@Nullable Object instance) {
        if (!(instance instanceof Plugin)) {
            return null;
        }
        PluginDescription description = ((Plugin) instance).getDescription();
        if (description == null) {
            return null;
        }
        String author = description.getAuthor();
        List<String> authors = author == null
                ? Collections.<String>emptyList() : Collections.singletonList(author);
        return new OwnerInfo(description.getName(), description.getVersion(), authors);
    }
}
