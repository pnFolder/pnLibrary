package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;

/** A native command adapter that remembers what was registered with it and what was closed. */
final class RecordingCommands implements PlatformCommandAdapter {
    CommandDefinition command;
    PlatformCommandDispatcher dispatcher;
    int registerCalls;
    int unregisterCalls;
    int closeCalls;
    boolean failNextRegistration;
    String failingCloseName;
    final Set<String> unregisteredNames = new LinkedHashSet<>();

    @NotNull
    @Override
    public PlatformCommandRegistration register(@NotNull Object owner, @NotNull final CommandDefinition registered,
            @NotNull PlatformCommandDispatcher dispatcher) {
        registerCalls++;
        if (failNextRegistration) {
            failNextRegistration = false;
            throw new IllegalStateException("native failure");
        }
        this.command = registered;
        this.dispatcher = dispatcher;
        return () -> {
            unregisterCalls++;
            unregisteredNames.add(registered.getName());
            if (registered.getName().equals(failingCloseName)) {
                throw new IllegalStateException("close failed");
            }
        };
    }

    @Override
    public void close() {
        closeCalls++;
    }
}
