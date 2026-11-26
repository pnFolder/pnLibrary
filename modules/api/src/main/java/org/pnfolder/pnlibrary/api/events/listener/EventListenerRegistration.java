package org.pnfolder.pnlibrary.api.events.listener;

import org.jetbrains.annotations.NotNull;

/** Registration containing every annotated handler discovered on one listener. */
public interface EventListenerRegistration extends AutoCloseable {
    /** Registered listener instance. */
    @NotNull
    Listener getListener();

    /** Number of handler methods registered from the listener. */
    int getHandlerCount();

    /** Whether all handler subscriptions have been removed. */
    boolean isClosed();

    /** Removes every handler belonging to this registration. Safe to repeat. */
    @Override
    void close();
}
