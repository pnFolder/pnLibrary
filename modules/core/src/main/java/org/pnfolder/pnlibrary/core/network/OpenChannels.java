package org.pnfolder.pnlibrary.core.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * The channels opened on one proxy or game server: one channel per name, registered with the platform when it opens
 * and unregistered when it closes. The proxy and the game server differ only in the channel they build.
 *
 * @param <C> the channel type
 */
public class OpenChannels<C extends ClosableChannel> {
    /** Builds the channel {@code name}; {@code release} is what the channel calls to close itself. */
    @FunctionalInterface
    public interface Factory<C> {
        C create(Key key, Runnable release);
    }

    private final Map<String, C> open = new LinkedHashMap<>();
    private final Consumer<Key> register;
    private final Consumer<Key> unregister;
    private final Factory<C> factory;

    /** Creates the set; {@code register} and {@code unregister} get a channel key as it opens or closes. */
    protected OpenChannels(@NotNull Consumer<Key> register, @NotNull Consumer<Key> unregister,
                           @NotNull Factory<C> factory) {
        this.register = register;
        this.unregister = unregister;
        this.factory = factory;
    }

    /** Opens {@code key} or returns the open channel of that key. */
    public synchronized @NotNull C channel(@NotNull Key key) {
        ChannelName.check(key);
        String name = key.asString();
        C existing = open.get(name);
        if (existing != null) {
            return existing;
        }
        register.accept(key);
        C created = factory.create(key, () -> release(key));
        open.put(name, created);
        return created;
    }

    /** The open channels, oldest first. */
    public synchronized @NotNull List<C> channels() {
        return new ArrayList<>(open.values());
    }

    /** Whether {@code name} is open here. */
    public synchronized boolean owns(@NotNull String name) {
        return open.containsKey(name);
    }

    /** The open channel {@code name}, or null. */
    protected synchronized C find(String name) {
        return open.get(name);
    }

    /** Closes every channel, for the shutdown. */
    public void close() {
        for (C channel : channels()) {
            channel.close();
        }
    }

    private synchronized void release(Key key) {
        C channel = open.remove(key.asString());
        if (channel == null) {
            return;
        }
        channel.markClosed();
        unregister.accept(key);
    }
}
