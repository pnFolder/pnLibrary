package org.pnfolder.pnlibrary.core.events.player;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.player.PlayerJoinedEvent;
import org.pnfolder.pnlibrary.api.events.player.PlayerLeftEvent;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Turns each platform's native join and quit notifications into the portable player events.
 *
 * <p>Platform adapters call this from their own listeners and name no event type themselves, so a native class
 * called {@code PlayerJoinEvent} never clashes with the library one. Publishing is best effort: a closed event
 * service during shutdown simply drops the announcement.
 */
public final class PlayerLifecyclePublisher {
    private PlayerLifecyclePublisher() {
    }

    /** Announces that {@code uniqueId} joined. */
    public static void joined(@NotNull final PnLibrary library, @NotNull final UUID uniqueId, @NotNull final String name) {
        publish(library, new Supplier<Event>() {
            @Override
            public Event get() {
                return new PlayerJoinedEvent(uniqueId, name, library.getAudiences().player(uniqueId));
            }
        });
    }

    /** Announces that {@code uniqueId} left. */
    public static void left(@NotNull final PnLibrary library, @NotNull final UUID uniqueId, @NotNull final String name) {
        publish(library, new Supplier<Event>() {
            @Override
            public Event get() {
                return new PlayerLeftEvent(uniqueId, name, library.getAudiences().player(uniqueId));
            }
        });
    }

    private static void publish(PnLibrary library, Supplier<Event> create) {
        try {
            library.getEvents().callEvent(create.get());
        } catch (IllegalStateException closed) {
            // The event service or audience service is already closed: the server is shutting down.
        }
    }
}
