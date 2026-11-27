package org.pnfolder.pnlibrary.api.network.channel;

import java.util.List;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;

/**
 * Opens plugin-message channels of one kind: {@link NetworkProxy} on a proxy, {@link ServerNetwork} on a game server.
 *
 * @param <C> the channel type of this side
 */
public interface Channels<C extends MessageChannel> {
    /**
     * Opens the channel {@code key} or returns the one already open. Use your own namespace: {@code Key.key("myplugin",
     * "main")}.
     *
     * @throws IllegalArgumentException when the namespace is {@code minecraft}, which the game keeps for itself
     */
    @NotNull C channel(@NotNull Key key);

    /** The channels opened through {@link #channel(String)} that are still open, oldest first. */
    @NotNull List<C> channels();
}
