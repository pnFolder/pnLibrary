package org.pnfolder.pnlibrary.velocity.network;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.NetworkServer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collection;

/** {@link NetworkServer} backed by a Velocity {@link RegisteredServer}. */
public final class VelocityNetworkServer implements NetworkServer, ForwardingAudience.Single {
    private final RegisteredServer server;

    /** Wraps the native {@code server}. */
    public VelocityNetworkServer(@NotNull RegisteredServer server) {
        this.server = server;
    }

    @NotNull
    @Override
    public Audience audience() {
        return server;
    }

    @NotNull
    @Override
    public String getName() {
        return server.getServerInfo().getName();
    }

    @NotNull
    @Override
    public InetSocketAddress getAddress() {
        return server.getServerInfo().getAddress();
    }

    @Override
    public int getOnlinePlayers() {
        return server.getPlayersConnected().size();
    }

    @Nullable
    @Override
    public Duration getPing() {
        Collection<Player> players = server.getPlayersConnected();
        if (players.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Player player : players) {
            sum += player.getPing();
        }
        return Duration.ofMillis((long) (sum / players.size()));
    }

    @Override
    public boolean send(@NotNull String channel, @NotNull byte[] bytes) {
        return server.sendPluginMessage(MinecraftChannelIdentifier.from(channel), bytes);
    }
}
