package org.pnfolder.pnlibrary.velocity.network;

import java.util.Objects;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.network.NetworkPlayer;
import org.pnfolder.pnlibrary.api.network.NetworkServer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;

/** A player connected to the Velocity proxy. */
public final class VelocityNetworkPlayer implements NetworkPlayer {
    private final Player player;

    /** Wraps the native {@code player}. */
    public VelocityNetworkPlayer(@NotNull Player player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    /** Wraps the native {@code player} as a portable network player. */
    @NotNull
    public static NetworkPlayer of(@NotNull Player player) {
        return new VelocityNetworkPlayer(player);
    }

    /** Returns the wrapped native player. */
    @NotNull
    public Player getNativePlayer() {
        return player;
    }

    @NotNull
    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Nullable
    @Override
    public InetSocketAddress getAddress() {
        return player.getRemoteAddress();
    }

    @NotNull
    @Override
    public Duration getPing() {
        return Duration.ofMillis(player.getPing());
    }

    @Nullable
    @Override
    public NetworkServer getCurrentServer() {
        ServerConnection connection = player.getCurrentServer().orElse(null);
        return connection != null ? new VelocityNetworkServer(connection.getServer()) : null;
    }

    @NotNull
    @Override
    public Pointers pointers() {
        return AudienceExtensions.playerPointers(
                player::getUsername,
                player.getUniqueId(),
                () -> Component.text(player.getUsername()),
                player::getEffectiveLocale,
                player::hasPermission);
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        player.sendMessage(message);
    }

    @Override
    public void sendActionBar(@NotNull Component message) {
        player.sendActionBar(message);
    }

    @Override
    public void playSound(@NotNull Sound sound) {
        player.playSound(sound);
    }

    @Override
    public void showTitle(@NotNull Title title) {
        player.showTitle(title);
    }

    @Override
    public void disconnect(@NotNull Component reason) {
        player.disconnect(reason);
    }
}
