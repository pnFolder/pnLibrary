package org.pnfolder.pnlibrary.core.network;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The listeners of one channel; a failing listener is logged and does not stop the others. */
public final class ListenerSet<L> {
    private final String channel;
    private final BiConsumer<String, Throwable> failures;
    private final List<L> listeners = new CopyOnWriteArrayList<>();

    /** {@code failures} gets the message and the error of a listener that threw. */
    public ListenerSet(String channel, BiConsumer<String, Throwable> failures) {
        this.channel = channel;
        this.failures = failures;
    }

    public void add(L listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void remove(L listener) {
        listeners.remove(listener);
    }

    public void clear() {
        listeners.clear();
    }

    public void deliver(Consumer<L> call) {
        for (L listener : listeners) {
            try {
                call.accept(listener);
            } catch (RuntimeException error) {
                failures.accept("A listener of channel " + channel + " failed", error);
            }
        }
    }
}
