package org.pnfolder.pnlibrary.core.network.backend;

import net.kyori.adventure.key.Key;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.network.OpenChannels;

/**
 * The channels opened on one game server. A platform gives it the native register and unregister calls and the way
 * to carry a message to the proxy, hands every message from the proxy to {@link #dispatch} and calls {@link #flush}
 * when a player joins.
 */
public final class BackendChannels extends OpenChannels<BackendChannel> {
    /** Carries one message to the proxy through a player, if one is online. */
    @FunctionalInterface
    public interface Outgoing {
        /** Returns {@code false} when no player can carry the message now. */
        boolean send(String channel, byte[] data);
    }

    /** Creates the set; {@code failures} gets the message and error of a listener that threw. */
    public BackendChannels(@NotNull Consumer<Key> register, @NotNull Consumer<Key> unregister,
                           @NotNull Outgoing outgoing, @NotNull BiConsumer<String, Throwable> failures) {
        super(register, unregister, (name, release) -> new BackendChannel(name, release, outgoing, failures));
    }

    /** Passes a message from the proxy to the listeners of {@code name}; false when the channel is not open here. */
    public boolean dispatch(@NotNull String name, @NotNull byte[] data) {
        BackendChannel channel = find(name);
        if (channel == null) {
            return false;
        }
        channel.deliver(data);
        return true;
    }

    /** Sends what waited for a player; call it when one joins. */
    public void flush() {
        for (BackendChannel channel : channels()) {
            channel.flush();
        }
    }
}
