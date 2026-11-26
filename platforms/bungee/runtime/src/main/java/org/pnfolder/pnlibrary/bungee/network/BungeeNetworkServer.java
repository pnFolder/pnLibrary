package org.pnfolder.pnlibrary.bungee.network;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.NetworkPlayer;
import org.pnfolder.pnlibrary.api.network.NetworkServer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A backend server registered on the BungeeCord proxy. */
@SuppressWarnings("deprecation")
public final class BungeeNetworkServer implements NetworkServer {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final ServerInfo server;

    /** Wraps the native {@code server}. */
    public BungeeNetworkServer(@NotNull ServerInfo server) {
        this.server = server;
    }

    @NotNull
    @Override
    public Iterable<? extends Audience> audiences() {
        List<Audience> players = new ArrayList<>();
        for (ProxiedPlayer player : server.getPlayers()) {
            players.add(BungeeNetworkPlayer.of(player));
        }
        return players;
    }

    @NotNull
    @Override
    public String getName() {
        return server.getName();
    }

    @NotNull
    @Override
    public InetSocketAddress getAddress() {
        return server.getAddress();
    }

    @Override
    public int getOnlinePlayers() {
        return server.getPlayers().size();
    }

    @Nullable
    @Override
    public Duration getPing() {
        Collection<ProxiedPlayer> players = server.getPlayers();
        if (players.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (ProxiedPlayer player : players) {
            sum += (long) player.getPing();
        }
        return Duration.ofMillis((long) (sum / players.size()));
    }

    @NotNull
    @Override
    public Component getMotd() {
        return LEGACY.deserialize(server.getMotd());
    }

    @Override
    public boolean isRestricted() {
        return server.isRestricted();
    }

    @Override
    public boolean send(@NotNull String channel, @NotNull byte[] bytes) {
        return server.sendData(channel, bytes, false);
    }

    @Override
    public boolean canAccess(@NotNull NetworkPlayer player) {
        if (!(player instanceof BungeeNetworkPlayer)) {
            return true;
        }
        return server.canAccess(((BungeeNetworkPlayer) player).getNativePlayer());
    }
}
