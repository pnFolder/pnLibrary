package org.pnfolder.pnlibrary.api.events.bus;

/**
 * How an event was dispatched, shown by {@code EventTrace} and {@code /pn bus}.
 *
 * <p>Listeners of a {@link #SYNC} dispatch run on the calling thread, so they may touch the game world when the caller
 * is on the server thread. Listeners of an {@link #ASYNC} dispatch run on a background thread and must not touch the
 * world.
 */
public enum DispatchMode {
    /** {@link EventService#callEvent}: on the calling thread, the result is final when the call returns. */
    SYNC,
    /**
     * {@link EventService#callEventAsync}: on the bus thread or a given executor, the result arrives through a
     * {@link java.util.concurrent.CompletionStage}.
     */
    ASYNC
}
