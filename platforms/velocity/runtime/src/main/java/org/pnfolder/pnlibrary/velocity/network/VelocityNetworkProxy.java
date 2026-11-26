package org.pnfolder.pnlibrary.velocity.network;

import java.util.Objects;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.NetworkPlayer;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.core.menu.OwnerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** {@link NetworkProxy} backed by a Velocity {@link ProxyServer}. */
public final class VelocityNetworkProxy implements NetworkProxy, ForwardingAudience.Single {
    private final ProxyServer server;

    /** Wraps the native {@code server}. */
    public VelocityNetworkProxy(@NotNull ProxyServer server) {
        this.server = Objects.requireNonNull(server, "server");
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

    @Override
    public void registerChannel(@NotNull String channel) {
        server.getChannelRegistrar().register(MinecraftChannelIdentifier.from(channel));
    }

    @Override
    public void unregisterChannel(@NotNull String channel) {
        server.getChannelRegistrar().unregister(MinecraftChannelIdentifier.from(channel));
    }

    @Override
    public void stop(@Nullable Component reason) {
        if (reason != null) {
            server.shutdown(reason);
        } else {
            server.shutdown();
        }
    }

    /** Returns the pnLibrary owner info of a Velocity plugin instance, or null when {@code instance} is not one. */
    @Nullable
    public OwnerInfo describePlugin(@Nullable Object instance) {
        if (instance == null) {
            return null;
        }
        PluginContainer container = server.getPluginManager().fromInstance(instance).orElse(null);
        if (container == null) {
            return null;
        }
        PluginDescription description = container.getDescription();
        return new OwnerInfo(description.getName().orElse(description.getId()),
                description.getVersion().orElse(null), description.getAuthors());
    }
}
