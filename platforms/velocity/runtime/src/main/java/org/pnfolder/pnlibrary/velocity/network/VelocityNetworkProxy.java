package org.pnfolder.pnlibrary.velocity.network;

import org.pnfolder.pnlibrary.core.network.AddedServers;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.net.InetSocketAddress;
import java.util.Objects;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
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
import java.util.UUID;
import java.util.function.BiConsumer;

/** {@link NetworkProxy} backed by a Velocity {@link ProxyServer}. */
public final class VelocityNetworkProxy implements NetworkProxy, ForwardingAudience.Single {
    private final ProxyServer server;
    private final ProxyChannels channels;
    private final AddedServers added;

    /** Wraps the native {@code server}. */
    public VelocityNetworkProxy(@NotNull ProxyServer server, @NotNull BiConsumer<String, Throwable> failures) {
        this.server = Objects.requireNonNull(server, "server");
        this.channels = new ProxyChannels(this::registerChannel, this::unregisterChannel, this::getServers, failures);
        this.added = new AddedServers(new AddedServers.Platform() {
            @Override
            public boolean exists(String name) {
                return findServer(name) != null;
            }

            @Override
            public NetworkServer register(String name, InetSocketAddress address) {
                return new VelocityNetworkServer(server.registerServer(new ServerInfo(name, address)));
            }

            @Override
            public void unregister(String name) {
                RegisteredServer registered = findServer(name);
                if (registered != null) {
                    server.unregisterServer(registered.getServerInfo());
                }
            }
        });
    }

    /** Starts passing plugin messages to the channels; the returned handle stops that and closes the channels. */
    @NotNull
    public AutoCloseable listenFor(@NotNull Object owner) {
        VelocityChannelListener listener = new VelocityChannelListener(channels);
        server.getEventManager().register(owner, listener);
        return () -> {
            server.getEventManager().unregisterListener(owner, listener);
            channels.close();
            added.close();
        };
    }

    @NotNull
    @Override
    public Audience audience() {
        return server;
    }

    @NotNull
    @Override
    public String getName() {
        return server.getVersion().getName();
    }

    @NotNull
    @Override
    public String getVersion() {
        return server.getVersion().getVersion();
    }

    @Override
    public int getPlayerCount() {
        return server.getPlayerCount();
    }

    @NotNull
    @Override
    public List<NetworkPlayer> getPlayers() {
        List<NetworkPlayer> players = new ArrayList<>();
        for (Player player : server.getAllPlayers()) {
            players.add(new VelocityNetworkPlayer(player));
        }
        return players;
    }

    @NotNull
    @Override
    public List<NetworkServer> getServers() {
        List<NetworkServer> servers = new ArrayList<>();
        for (RegisteredServer registered : server.getAllServers()) {
            servers.add(new VelocityNetworkServer(registered));
        }
        return servers;
    }

    @Nullable
    @Override
    public NetworkPlayer player(@NotNull String name) {
        Player player = server.getPlayer(name).orElse(null);
        return player != null ? new VelocityNetworkPlayer(player) : null;
    }

    @Nullable
    @Override
    public NetworkPlayer player(@NotNull UUID uuid) {
        Player player = server.getPlayer(uuid).orElse(null);
        return player != null ? new VelocityNetworkPlayer(player) : null;
    }

    @Nullable
    @Override
    public NetworkServer server(@NotNull String name) {
        RegisteredServer registered = server.getServer(name).orElse(null);
        return registered != null ? new VelocityNetworkServer(registered) : null;
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

    private RegisteredServer findServer(String name) {
        return server.getServer(name).orElse(null);
    }

    @NotNull
    @Override
    public NetworkChannel channel(@NotNull Key key) {
        return channels.channel(key);
    }

    @NotNull
    @Override
    public List<NetworkChannel> channels() {
        return new ArrayList<NetworkChannel>(channels.channels());
    }

    @Override
    public void registerChannel(@NotNull Key channel) {
        server.getChannelRegistrar().register(identifier(channel));
    }

    @Override
    public void unregisterChannel(@NotNull Key channel) {
        server.getChannelRegistrar().unregister(identifier(channel));
    }

    MinecraftChannelIdentifier identifier(Key key) {
        return MinecraftChannelIdentifier.from(key);
    }

    @Override
    public void stop(@Nullable Component reason) {
        if (reason != null) {
            server.shutdown(reason);
        } else {
            server.shutdown();
        }
    }
}
