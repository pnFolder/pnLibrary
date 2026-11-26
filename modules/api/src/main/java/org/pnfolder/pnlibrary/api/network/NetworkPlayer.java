package org.pnfolder.pnlibrary.api.network;

import java.net.InetSocketAddress;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;

/**
 * A player connected through a proxy: an {@link OnlinePlayer} plus what only a proxy knows, such as the backend
 * server the player is on.
 *
 * <pre>{@code
 * NetworkPlayer player = library.getProxy().player("Steve");
 * String server = player.getCurrentServer().getName();   // "lobby"
 * player.disconnect(Component.text("Maintenance"));
 * }</pre>
 *
 * <p>Obtain one from {@link NetworkProxy#player(String)} or {@link NetworkProxy#getPlayers()}.
 */
public interface NetworkPlayer extends OnlinePlayer {
    /** The remote address of the client, or {@code null} when the platform does not expose it. */
    @Nullable InetSocketAddress getAddress();

    /** The last measured latency between the proxy and the client. */
    @NotNull Duration getPing();

    /** The backend server the player is on, or {@code null} while connecting. */
    @Nullable NetworkServer getCurrentServer();

    /** Disconnects the player and shows {@code reason} on the disconnect screen. */
    void disconnect(@NotNull Component reason);

    /** Disconnects the player with an empty disconnect screen. */
    default void disconnect() {
        disconnect(Component.empty());
    }
}
