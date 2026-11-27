package org.pnfolder.pnlibrary.bungee.network;

import org.pnfolder.pnlibrary.core.network.AddedServers;
import java.net.InetSocketAddress;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel;
import org.pnfolder.pnlibrary.api.network.NetworkPlayer;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.core.network.proxy.ProxyChannels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** {@link NetworkProxy} backed by the BungeeCord proxy. */
public final class BungeeNetworkProxy implements NetworkProxy {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final ProxyServer proxy;
    private final ProxyChannels channels;
    private final AddedServers added;

    /** Wraps the native {@code proxy}. */
    public BungeeNetworkProxy(@NotNull ProxyServer proxy, @NotNull BiConsumer<String, Throwable> failures) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.channels = new ProxyChannels(this::registerChannel, this::unregisterChannel, this::getServers, failures);
        this.added = new AddedServers(new AddedServers.Platform() {
            @Override
            public boolean exists(String name) {
                return server(name) != null;
            }

            @Override
            public NetworkServer register(String name, InetSocketAddress address) {
                ServerInfo info = proxy.constructServerInfo(name, address, "", false);
                proxy.getServers().put(name, info);
                return new BungeeNetworkServer(info);
            }

            @Override
            public void unregister(String name) {
                proxy.getServers().remove(name);
            }
        });
    }

    /** Starts passing plugin messages to the channels; the returned handle stops that and closes the channels. */
    @NotNull
    public AutoCloseable listenFor(@NotNull Plugin owner) {
        BungeeChannelListener listener = new BungeeChannelListener(channels);
        proxy.getPluginManager().registerListener(owner, listener);
        return () -> {
            proxy.getPluginManager().unregisterListener(listener);
            channels.close();
            added.close();
        };
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
        List<NetworkPlayer> players = new ArrayList<>(proxy.getOnlineCount());
        for (ProxiedPlayer player : proxy.getPlayers()) {
            players.add(new BungeeNetworkPlayer(player));
        }
        return players;
    }

    @NotNull
    @Override
    public List<NetworkServer> getServers() {
        Map<String, ServerInfo> all = proxy.getServers();
        List<NetworkServer> servers = new ArrayList<>(all.size());
        for (ServerInfo server : all.values()) {
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

    @NotNull
    @Override
    public NetworkServer addServer(@NotNull String name, @NotNull InetSocketAddress address) {
        return added.add(name, address);
    }

    @Override
    public boolean removeServer(@NotNull NetworkServer server) {
        return added.remove(server);
    }

    @NotNull
    @Override
    public NetworkChannel channel(@NotNull Key key) {
        return channels.channel(key);
    }

    @NotNull
    @Override
    public List<NetworkChannel> channels() {
        return new ArrayList<>(channels.channels());
    }

    @Override
    public void registerChannel(@NotNull Key channel) {
        proxy.registerChannel(channel.asString());
    }

    @Override
    public void unregisterChannel(@NotNull Key channel) {
        proxy.unregisterChannel(channel.asString());
    }

    @Override
    public void stop(@Nullable Component reason) {
        if (reason != null) {
            proxy.stop(LEGACY.serialize(reason));
        } else {
            proxy.stop();
        }
    }
}
