package org.pnfolder.pnlibrary.api.network;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A backend game server registered behind the proxy.
 *
 * <pre>{@code
 * NetworkServer lobby = library.getProxy().server("lobby");
 * lobby.sendMessage(Component.text("Restart in 5 minutes"));   // everyone on this server
 * lobby.send("myplugin:sync", payload);
 * }</pre>
 *
 * <p>Properties the platform does not expose have neutral defaults: {@link #getMotd()} is {@code null} and
 * {@link #isRestricted()} is {@code false} on Velocity.
 */
public interface NetworkServer extends ForwardingAudience {
    /** The name of the server as registered in the proxy configuration. */
    @NotNull String getName();

    /** The address the proxy connects to. */
    @NotNull InetSocketAddress getAddress();

    /** The number of players currently on this server. */
    int getOnlinePlayers();

    /** The players on this server right now. */
    @NotNull List<NetworkPlayer> getPlayers();

    /**
     * Moves every player on this server to {@code target}, for example before the server is removed. The stage
     * completes with the number of players that are on the target afterwards, never exceptionally.
     */
    default @NotNull CompletionStage<Integer> sendPlayers(@NotNull NetworkServer target) {
        List<CompletableFuture<ConnectResult>> moves = new ArrayList<>();
        for (NetworkPlayer player : getPlayers()) {
            moves.add(player.connect(target).toCompletableFuture());
        }
        return CompletableFuture.allOf(moves.toArray(new CompletableFuture<?>[0])).handle((ignored, error) -> {
            int moved = 0;
            for (CompletableFuture<ConnectResult> move : moves) {
                if (!move.isCompletedExceptionally() && move.join().isOnTarget()) {
                    moved++;
                }
            }
            return moved;
        });
    }

    /** The average latency of the players on this server; {@code null} while nobody is on it. */
    @Nullable Duration getPing();

    /** The server MOTD; {@code null} on platforms that do not expose it. */
    default @Nullable Component getMotd() {
        return null;
    }

    /** Whether the server rejects players without explicit permission; {@code false} on platforms without that setting. */
    default boolean isRestricted() {
        return false;
    }

    /** Sends plugin-message {@code bytes} to this server over {@code channel}; {@code false} when it cannot be reached. */
    boolean send(@NotNull String channel, @NotNull byte[] bytes);

    /** Whether {@code player} may connect to this server; {@code true} on platforms without an access list. */
    default boolean canAccess(@NotNull NetworkPlayer player) {
        return true;
    }
}
