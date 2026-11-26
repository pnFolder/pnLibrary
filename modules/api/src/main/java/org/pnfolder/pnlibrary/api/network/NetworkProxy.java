package org.pnfolder.pnlibrary.api.network;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public interface NetworkProxy extends ForwardingAudience {
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

    /** Starts receiving plugin messages on {@code channel}. */
    void registerChannel(@NotNull String channel);

    /** Stops receiving plugin messages on {@code channel}. */
    void unregisterChannel(@NotNull String channel);

    /** Shuts the proxy down, kicking players with {@code reason} when given. */
    void stop(@Nullable Component reason);

    /** Shuts the proxy down without a kick message. */
    default void stop() {
        stop(null);
    }
}
