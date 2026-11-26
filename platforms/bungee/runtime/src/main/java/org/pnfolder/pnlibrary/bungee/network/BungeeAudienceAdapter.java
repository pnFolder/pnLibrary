package org.pnfolder.pnlibrary.bungee.network;

import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.audience.Console;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter;
import org.pnfolder.pnlibrary.spi.audience.PlatformConsole;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Turns BungeeCord senders and players into portable audiences. */
@SuppressWarnings("deprecation")
public final class BungeeAudienceAdapter implements PlatformAudienceAdapter {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Plugin plugin;

    /** Creates an adapter that reads players and the console from the proxy of {@code plugin}. */
    public BungeeAudienceAdapter(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @NotNull
    @Override
    public Console console() {
        return new PlatformConsole(wrap(plugin.getProxy().getConsole()));
    }

    @Nullable
    @Override
    public OnlinePlayer player(@NotNull UUID uniqueId) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(uniqueId);
        return player != null ? BungeeNetworkPlayer.of(player) : null;
    }

    @Nullable
    @Override
    public Audience of(@NotNull Object nativeSender) {
        if (nativeSender instanceof ProxiedPlayer) {
            return BungeeNetworkPlayer.of((ProxiedPlayer) nativeSender);
        }
        return nativeSender instanceof CommandSender ? wrap((CommandSender) nativeSender) : null;
    }

    @NotNull
    @Override
    public List<OnlinePlayer> players() {
        List<OnlinePlayer> players = new ArrayList<>();
        for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
            players.add(BungeeNetworkPlayer.of(player));
        }
        return players;
    }

    private static Audience wrap(CommandSender sender) {
        return new Audience() {
            @NotNull
            @Override
            public Pointers pointers() {
                return AudienceExtensions.senderPointers(sender.getName(), sender::hasPermission);
            }

            @Override
            public void sendMessage(@NotNull Component message) {
                sender.sendMessage(TextComponent.fromLegacyText(LEGACY.serialize(message)));
            }

            @Override
            public void sendActionBar(@NotNull Component message) {
                sendMessage(message);
            }
        };
    }
}
