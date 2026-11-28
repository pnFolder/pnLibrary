package org.pnfolder.pnlibrary.bungee.network;

import java.util.Objects;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.network.NetworkPlayer;
import org.pnfolder.pnlibrary.api.network.NetworkServer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerConnectEvent;
import org.pnfolder.pnlibrary.api.network.ConnectResult;

/** A player connected to the BungeeCord proxy. */
@SuppressWarnings("deprecation")
public final class BungeeNetworkPlayer implements NetworkPlayer {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final ProxiedPlayer player;

    /** Wraps the native {@code player}. */
    public BungeeNetworkPlayer(@NotNull ProxiedPlayer player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    /** Wraps the native {@code player} as a portable network player. */
    @NotNull
    public static NetworkPlayer of(@NotNull ProxiedPlayer player) {
        return new BungeeNetworkPlayer(player);
    }

    /** Returns the wrapped native player. */
    @NotNull
    public ProxiedPlayer getNativePlayer() {
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
        return player.getAddress();
    }

    @NotNull
    @Override
    public Duration getPing() {
        return Duration.ofMillis((long) player.getPing());
    }

    @Nullable
    @Override
    public NetworkServer getCurrentServer() {
        Server server = player.getServer();
        return server != null ? new BungeeNetworkServer(server.getInfo()) : null;
    }

    @NotNull
    @Override
    public CompletionStage<ConnectResult> connect(@NotNull NetworkServer target) {
        if (!(target instanceof BungeeNetworkServer)) {
            return CompletableFuture.completedFuture(ConnectResult.FAILED);
        }
        ServerInfo info = ((BungeeNetworkServer) target).getNativeServer();
        Server current = player.getServer();
        if (current != null && current.getInfo().equals(info)) {
            return CompletableFuture.completedFuture(ConnectResult.ALREADY_THERE);
        }
        if (!info.canAccess(player)) {
            return CompletableFuture.completedFuture(ConnectResult.DENIED);
        }
        CompletableFuture<ConnectResult> result = new CompletableFuture<>();
        player.connect(info, (success, error) -> result.complete(success != null && success
                ? ConnectResult.CONNECTED : ConnectResult.FAILED), ServerConnectEvent.Reason.PLUGIN);
        return result;
    }

    @NotNull
    @Override
    public Pointers pointers() {
        return AudienceExtensions.playerPointers(
                player::getName,
                player.getUniqueId(),
                () -> LEGACY.deserialize(player.getDisplayName()),
                player::getLocale,
                player::hasPermission);
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        player.sendMessage(TextComponent.fromLegacyText(LEGACY.serialize(message)));
    }

    @Override
    public void sendActionBar(@NotNull Component message) {
        player.sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(LEGACY.serialize(message)));
    }

    @Override
    public void disconnect(@NotNull Component reason) {
        player.disconnect(TextComponent.fromLegacyText(LEGACY.serialize(reason)));
    }
}
