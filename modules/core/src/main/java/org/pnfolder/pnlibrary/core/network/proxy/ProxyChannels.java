package org.pnfolder.pnlibrary.core.network.proxy;

import net.kyori.adventure.key.Key;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.core.network.OpenChannels;

/**
 * The channels opened on one proxy. A platform gives it the native register and unregister calls and the server
 * list, and hands every plugin message from a server to {@link #dispatch}.
 */
public final class ProxyChannels extends OpenChannels<ProxyChannel> {
    /**
     * Creates the set; {@code servers} is asked each time a message is broadcast, {@code failures} gets the message
     * and error of a listener that threw.
     */
    public ProxyChannels(@NotNull Consumer<Key> register, @NotNull Consumer<Key> unregister,
                         @NotNull Supplier<List<NetworkServer>> servers,
                         @NotNull BiConsumer<String, Throwable> failures) {
        super(register, unregister, (name, release) -> new ProxyChannel(name, release, servers, failures));
    }

    /**
     * Passes a message from {@code from} to the listeners of {@code name}. Returns {@code true} when the channel is
     * open here, so the platform should cancel the message and keep it away from the players.
     */
    public boolean dispatch(@NotNull String name, @NotNull NetworkServer from, @NotNull byte[] data) {
        ProxyChannel channel = find(name);
        if (channel == null) {
            return false;
        }
        channel.deliver(from, data);
        return true;
    }
}
