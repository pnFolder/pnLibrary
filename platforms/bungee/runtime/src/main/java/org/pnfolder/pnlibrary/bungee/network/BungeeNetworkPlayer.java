package org.pnfolder.pnlibrary.bungee.network;

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

/** A player connected to the BungeeCord proxy. */
@SuppressWarnings("deprecation")
public final class BungeeNetworkPlayer implements NetworkPlayer {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final ProxiedPlayer player;

    /** Wraps the native {@code player}. */
    public BungeeNetworkPlayer(@NotNull ProxiedPlayer player) {
        this.player = player;
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
