package org.pnfolder.pnlibrary.core.events.registration;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.listener.EventListenerRegistration;
import org.pnfolder.pnlibrary.api.events.listener.EventSubscription;
import org.pnfolder.pnlibrary.api.events.listener.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** All the subscriptions made for the handlers of one listener object; closing it closes every one. */
public final class ListenerRegistration implements EventListenerRegistration {
    private final Listener listener;
    private final List<EventSubscription> subscriptions;
    private final AtomicBoolean registrationClosed = new AtomicBoolean(false);

    /** Groups {@code subscriptions} under the {@code listener} they were made for. */
    public ListenerRegistration(@NotNull Listener listener, @NotNull List<? extends EventSubscription> subscriptions) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.subscriptions = new ArrayList<>(subscriptions);
    }

    @NotNull
    @Override
    public Listener getListener() {
        return listener;
    }

    @Override
    public int getHandlerCount() {
        return subscriptions.size();
    }

    @Override
    public boolean isClosed() {
        if (registrationClosed.get()) {
            return true;
        }
        for (EventSubscription subscription : subscriptions) {
            if (!subscription.isClosed()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        if (!registrationClosed.compareAndSet(false, true)) {
            return;
        }
        for (EventSubscription subscription : subscriptions) {
            subscription.close();
        }
    }
}
