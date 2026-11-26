package org.pnfolder.pnlibrary.spi.commands;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared ownership and shutdown lifecycle for native command registrations. */
public abstract class TrackingPlatformCommandAdapter implements PlatformCommandAdapter {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<TrackedRegistration> registrations = new LinkedHashSet<>();

    /** Registers {@code command} natively so that native calls reach {@code dispatcher}. */
    @NotNull
    protected abstract PlatformCommandRegistration registerNative(
            @NotNull Object owner,
            @NotNull CommandDefinition command,
            @NotNull PlatformCommandDispatcher dispatcher);

    @NotNull
    @Override
    public final PlatformCommandRegistration register(
            @NotNull Object owner,
            @NotNull CommandDefinition command,
            @NotNull PlatformCommandDispatcher dispatcher) {
        if (closed.get()) {
            throw new IllegalStateException("Platform command adapter is closed");
        }
        TrackedRegistration registration = new TrackedRegistration(registerNative(owner, command, dispatcher));
        synchronized (registrations) {
            registrations.add(registration);
        }
        if (closed.get()) {
            registration.close();
        }
        return registration;
    }

    @Override
    public final void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<TrackedRegistration> pending;
        synchronized (registrations) {
            pending = new ArrayList<>(registrations);
        }
        Collections.reverse(pending);
        Throwable failure = null;
        for (TrackedRegistration registration : pending) {
            try {
                registration.close();
            } catch (Throwable error) {
                if (failure != null) {
                    failure.addSuppressed(error);
                } else {
                    failure = error;
                }
            }
        }
        synchronized (registrations) {
            registrations.clear();
        }
        if (failure != null) {
            sneakyThrow(failure);
        }
    }

    // Close must rethrow whatever the first registration threw, exactly as it was thrown.
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable error) throws E {
        throw (E) error;
    }

    private final class TrackedRegistration implements PlatformCommandRegistration {
        private final PlatformCommandRegistration delegate;
        private final AtomicBoolean registrationClosed = new AtomicBoolean(false);

        TrackedRegistration(PlatformCommandRegistration delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            if (!registrationClosed.compareAndSet(false, true)) {
                return;
            }
            synchronized (registrations) {
                registrations.remove(this);
            }
            delegate.close();
        }
    }
}
