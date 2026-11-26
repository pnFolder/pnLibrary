package org.pnfolder.pnlibrary.core.events;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;
import org.pnfolder.pnlibrary.api.events.listener.EventListenerRegistration;
import org.pnfolder.pnlibrary.api.events.listener.EventSubscription;
import org.pnfolder.pnlibrary.api.events.listener.Listener;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.events.inspect.EventHandlerInspector;
import org.pnfolder.pnlibrary.core.events.registration.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** The subscriptions of one plugin or module; closing the scope closes all of them. */
final class PluginEventScope implements EventScope {
    private final EventDispatcher dispatcher;
    private final PluginId pluginId;
    private final PluginId plugin;
    private final ModuleId module;
    private final AtomicBoolean scopeClosed = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<ListenerSubscription> ownedSubscriptions = new CopyOnWriteArrayList<>();

    PluginEventScope(EventDispatcher dispatcher, PluginId pluginId, PluginId plugin, @Nullable ModuleId module) {
        this.dispatcher = dispatcher;
        this.pluginId = pluginId;
        this.plugin = plugin;
        this.module = module;
    }

    PluginId getPlugin() {
        return plugin;
    }

    @Nullable
    ModuleId getModule() {
        return module;
    }

    @NotNull
    @Override
    public PluginId getPluginId() {
        return pluginId;
    }

    @Override
    public boolean isClosed() {
        return scopeClosed.get();
    }

    @NotNull
    @Override
    public EventListenerRegistration register(@NotNull Listener listener) {
        checkOpen();
        List<EventHandlerInspector.Handler> handlers = EventHandlerInspector.inspect(listener);
        if (handlers.isEmpty()) {
            throw new IllegalArgumentException(listener.getClass().getName() + " does not declare any @EventHandler methods");
        }
        List<EventSubscription> registered = new ArrayList<>();
        try {
            for (EventHandlerInspector.Handler handler : handlers) {
                registered.add(subscribeHandler(listener, handler));
            }
            return new ListenerRegistration(listener, registered);
        } catch (Throwable error) {
            for (final EventSubscription subscription : registered) {
                Failures.runCatchingNonFatal(() -> {
                    subscription.close();
                    return null;
                });
            }
            throw error;
        }
    }

    @NotNull
    @Override
    public <E extends Event> EventSubscription subscribe(
            @NotNull Class<E> eventType, int priority, boolean ignoreDenied, @NotNull Consumer<E> listener) {
        return subscribe(eventType, priority, ignoreDenied, listener, listener.getClass().getName());
    }

    private <E extends Event> EventSubscription subscribe(
            Class<E> eventType, int priority, boolean ignoreDenied, Consumer<E> listener, String source) {
        checkOpen();
        ListenerSubscription subscription = new ListenerSubscription(
                dispatcher, this, eventType, priority, ignoreDenied, listener, dispatcher.nextOrder(), source);
        ownedSubscriptions.add(subscription);
        dispatcher.addSubscription(subscription);
        if (dispatcher.isClosed() || scopeClosed.get()) {
            subscription.close();
        }
        return subscription;
    }

    @SuppressWarnings("unchecked")
    private EventSubscription subscribeHandler(final Listener listener, final EventHandlerInspector.Handler handler) {
        Class<Event> eventType = (Class<Event>) handler.getEventType();
        String source = listener.getClass().getName() + "#" + handler.getMethod().getName();
        Consumer<Event> consumer = event -> handler.invoke(listener, event);
        return subscribe(eventType, handler.getAnnotation().priority(), handler.getAnnotation().ignoreDenied(), consumer, source);
    }

    private void checkOpen() {
        if (dispatcher.isClosed() || scopeClosed.get()) {
            throw new IllegalStateException("EventScope is closed");
        }
    }

    @NotNull
    @Override
    public <E extends Event> E callEvent(@NotNull E event) {
        return dispatcher.callEvent(event);
    }

    @Override
    public void close() {
        closeInternal();
        dispatcher.removeScope(pluginId, this);
    }

    // Does not remove the scope from the dispatcher: the caller (close or unregisterAll) does that.
    void closeInternal() {
        if (!scopeClosed.compareAndSet(false, true)) {
            return;
        }
        for (ListenerSubscription subscription : ownedSubscriptions) {
            subscription.close();
        }
        ownedSubscriptions.clear();
    }

    void remove(ListenerSubscription subscription) {
        ownedSubscriptions.remove(subscription);
    }
}
