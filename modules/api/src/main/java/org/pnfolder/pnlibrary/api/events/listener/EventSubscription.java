package org.pnfolder.pnlibrary.api.events.listener;

/**
 * Handle for one typed listener registration.
 *
 * <p>Closing the handle removes only this listener and is safe to repeat.
 */
public interface EventSubscription extends AutoCloseable {
    /** Whether this listener has been removed. */
    boolean isClosed();

    /** Removes this listener from future dispatches. */
    @Override
    void close();
}
