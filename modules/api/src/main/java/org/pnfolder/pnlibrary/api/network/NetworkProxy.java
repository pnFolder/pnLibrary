package org.pnfolder.pnlibrary.api.network;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.channel.Channels;
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel;

/**
 * The proxy itself: its players, backend servers and plugin-message channels. Available on Velocity and BungeeCord
 * only; {@code library.getProxy()} is {@code null} on a game server.
 *
 * <pre>{@code
 * NetworkProxy proxy = library.getProxy();
 * proxy.sendMessage(Component.text("Welcome to " + proxy.getName()));   // every player on the proxy
 * proxy.server("lobby").getOnlinePlayers();
 * }</pre>
 */
public interface NetworkProxy extends ForwardingAudience, Channels<NetworkChannel> {
    /** The proxy software name, such as {@code Velocity}. */
    @NotNull String getName();

    /** The proxy software version. */
    @NotNull String getVersion();

    /** The number of players connected to the proxy. */
    int getPlayerCount();

    /** Every connected player. */
    @NotNull Collection<NetworkPlayer> getPlayers();

    /** Every registered backend server. */
    @NotNull List<NetworkServer> getServers();

    /** Returns the connected player called {@code name}, or {@code null}. */
    @Nullable NetworkPlayer player(@NotNull String name);

    /** Returns the connected player with {@code uuid}, or {@code null}. */
    @Nullable NetworkPlayer player(@NotNull UUID uuid);

    /** Returns the backend server registered as {@code name}, or {@code null}. */
    @Nullable NetworkServer server(@NotNull String name);

    /**
     * Adds a game server to the proxy while it runs, for example an arena or a lobby that starts on demand. It is not
     * saved to the proxy's configuration and goes away at the restart, or when {@link #removeServer} is called.
     * Players are not moved to it; they join it with the proxy's server command or a connection you make.
     *
     * @param name 1 to 64 letters, digits, {@code -} or {@code _}; it must not be used by another server (any case)
     * @param address a resolved address, {@code new InetSocketAddress("10.0.0.5", 25570)}
     * @throws IllegalArgumentException when the name is not valid or taken, or the address is not resolved
     */
    @NotNull NetworkServer addServer(@NotNull String name, @NotNull InetSocketAddress address);

    /**
     * Removes a server that {@link #addServer} added. The proxy's own servers stay: it returns {@code false} for them
     * and for a server that was already removed. Players on the removed server are not kicked by this call.
     */
    boolean removeServer(@NotNull NetworkServer server);

    /** Registers {@code channel} on the proxy only; nothing is delivered to you. Prefer {@link #channel(Key)}. */
    void registerChannel(@NotNull Key channel);

    /** Unregisters {@code channel} on the proxy; the counterpart of {@link #registerChannel(Key)}. */
    void unregisterChannel(@NotNull Key channel);

    /** Shuts the proxy down, kicking players with {@code reason} when given. */
    void stop(@Nullable Component reason);

    /** Shuts the proxy down without a kick message. */
    default void stop() {
        stop(null);
    }
}
