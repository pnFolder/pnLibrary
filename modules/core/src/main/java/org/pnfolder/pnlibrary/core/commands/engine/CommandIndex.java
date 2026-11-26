package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

/** Which command names are taken and which registration belongs to which definition and owner, under one lock. */
final class CommandIndex {
    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<String> reservedNames = new LinkedHashSet<>();
    private final Set<RegisteredCommand> registrations = new LinkedHashSet<>();
    private final IdentityHashMap<CommandDefinition, RegisteredCommand> byDefinition =
            new IdentityHashMap<>();
    private final IdentityHashMap<Object, Set<RegisteredCommand>> byOwner = new IdentityHashMap<>();

    /** Marks the index closed; false when it already was. */
    boolean close() {
        return closed.compareAndSet(false, true);
    }

    void reserve(Set<String> names) {
        synchronized (lock) {
            if (closed.get()) {
                throw new IllegalStateException("CommandService is closed");
            }
            for (String name : names) {
                if (reservedNames.contains(name)) {
                    throw new IllegalArgumentException("Command name or alias '" + name + "' is already registered");
                }
            }
            reservedNames.addAll(names);
        }
    }

    void release(Set<String> names) {
        synchronized (lock) {
            reservedNames.removeAll(names);
        }
    }

    /** Adds the registration; when the service closed meanwhile it gives the names back and refuses. */
    void add(RegisteredCommand registration, Set<String> names, Runnable undo) {
        synchronized (lock) {
            if (closed.get()) {
                reservedNames.removeAll(names);
                undo.run();
                throw new IllegalStateException("CommandService is closed");
            }
            registrations.add(registration);
            byDefinition.put(registration.getCommand(), registration);
            Set<RegisteredCommand> owned = byOwner.get(registration.getOwner());
            if (owned == null) {
                owned = Collections.newSetFromMap(new IdentityHashMap<>());
                byOwner.put(registration.getOwner(), owned);
            }
            owned.add(registration);
        }
    }

    void remove(RegisteredCommand registration, Set<String> names) {
        synchronized (lock) {
            registrations.remove(registration);
            byDefinition.remove(registration.getCommand());
            reservedNames.removeAll(names);
            Set<RegisteredCommand> owned = byOwner.get(registration.getOwner());
            if (owned == null) {
                return;
            }
            owned.remove(registration);
            if (owned.isEmpty()) {
                byOwner.remove(registration.getOwner());
            }
        }
    }

    List<RegisteredCommand> ownedBy(Object owner) {
        synchronized (lock) {
            Set<RegisteredCommand> owned = byOwner.get(owner);
            return owned == null ? new ArrayList<>() : new ArrayList<>(owned);
        }
    }

    List<RegisteredCommand> all() {
        synchronized (lock) {
            return new ArrayList<>(registrations);
        }
    }

    /** The registrations that are still open, captured once so names and count describe the same set. */
    List<RegisteredCommand> open() {
        List<RegisteredCommand> current = new ArrayList<>();
        for (RegisteredCommand registration : all()) {
            if (!registration.isClosed()) {
                current.add(registration);
            }
        }
        return current;
    }

    @Nullable
    RegisteredCommand active(CommandDefinition command) {
        synchronized (lock) {
            RegisteredCommand found = byDefinition.get(command);
            return found == null || found.isClosed() ? null : found;
        }
    }
}
