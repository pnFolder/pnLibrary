package org.pnfolder.pnlibrary.bukkit.audience;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.audience.Console;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter;
import org.pnfolder.pnlibrary.spi.audience.PlatformConsole;

/** Converts native server recipients to portable audiences and player views. */
public final class BukkitAudienceAdapter implements PlatformAudienceAdapter {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final BukkitAdventureTransport audiences;

    public BukkitAudienceAdapter(BukkitAdventureTransport audiences) {
        this.audiences = audiences;
    }

    @NotNull
    @Override
    public Console console() {
        return new PlatformConsole(wrap(Bukkit.getConsoleSender()));
    }

    @Override
    public OnlinePlayer player(@NotNull UUID uniqueId) {
        Player player = Bukkit.getPlayer(uniqueId);
        return player == null ? null : new BukkitOnlinePlayer(player, audiences);
    }

    @Override
    public Audience of(@NotNull Object nativeSender) {
        return nativeSender instanceof CommandSender ? wrap((CommandSender) nativeSender) : null;
    }

    @NotNull
    @Override
    public List<OnlinePlayer> players() {
        List<OnlinePlayer> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(new BukkitOnlinePlayer(player, audiences));
        }
        return players;
    }

    private Audience wrap(CommandSender sender) {
        if (sender instanceof Player) {
            return new BukkitOnlinePlayer((Player) sender, audiences);
        }
        return new BukkitSenderAudience(sender);
    }

    /** Uses chat for generic senders; action bars fall back to chat and sounds are dropped. */
    private static final class BukkitSenderAudience implements Audience {
        private final CommandSender sender;

        BukkitSenderAudience(CommandSender sender) {
            this.sender = sender;
        }

        @NotNull
        @Override
        public Pointers pointers() {
            return AudienceExtensions.senderPointers(sender.getName(), sender::hasPermission);
        }

        @Override
        public void sendMessage(@NotNull Component message) {
            sender.sendMessage(LEGACY.serialize(message));
        }

        @Override
        public void sendActionBar(@NotNull Component message) {
            sendMessage(message);
        }
    }
}
