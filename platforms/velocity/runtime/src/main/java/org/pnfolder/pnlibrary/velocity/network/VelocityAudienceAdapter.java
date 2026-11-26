package org.pnfolder.pnlibrary.velocity.network;

import java.util.Objects;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.pointer.Pointers;
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

/** Turns Velocity command sources and players into portable audiences. */
public final class VelocityAudienceAdapter implements PlatformAudienceAdapter {
    private final ProxyServer server;

    /** Creates an adapter that reads players and the console from {@code server}. */
    public VelocityAudienceAdapter(@NotNull ProxyServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @NotNull
    @Override
    public Console console() {
        return new PlatformConsole(wrap(server.getConsoleCommandSource()));
    }

    @Nullable
    @Override
    public OnlinePlayer player(@NotNull UUID uniqueId) {
        Player player = server.getPlayer(uniqueId).orElse(null);
        return player != null ? VelocityNetworkPlayer.of(player) : null;
    }

    @Nullable
    @Override
    public Audience of(@NotNull Object nativeSender) {
        if (nativeSender instanceof Player) {
            return VelocityNetworkPlayer.of((Player) nativeSender);
        }
        return nativeSender instanceof CommandSource ? wrap((CommandSource) nativeSender) : null;
    }

    @NotNull
    @Override
    public List<OnlinePlayer> players() {
        List<OnlinePlayer> players = new ArrayList<>();
        for (Player player : server.getAllPlayers()) {
            players.add(VelocityNetworkPlayer.of(player));
        }
        return players;
    }

    // The source is already an Adventure audience; this only gives the console a name and a permission check.
    private static Audience wrap(CommandSource source) {
        return new ForwardingAudience.Single() {
            @NotNull
            @Override
            public Audience audience() {
                return source;
            }

            @NotNull
            @Override
            public Pointers pointers() {
                String name = source instanceof ConsoleCommandSource ? "Console" : source.toString();
                return AudienceExtensions.senderPointers(name, source::hasPermission);
            }
        };
    }
}
