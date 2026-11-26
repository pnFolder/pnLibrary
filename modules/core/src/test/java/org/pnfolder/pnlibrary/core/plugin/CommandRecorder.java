package org.pnfolder.pnlibrary.core.plugin;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.commands.engine.CommandFeedbackScope;
import org.pnfolder.pnlibrary.core.commands.engine.CommandService;

/** A command service that lists the owners it registered and unregistered. */
public final class CommandRecorder implements CommandService {
    public final List<Object> unregisteredOwners = new ArrayList<>();
    public final List<Object> registeredOwners = new ArrayList<>();

    @Override
    public @NotNull CommandRegistration register(@NotNull Object owner, @NotNull CommandDefinition command,
                                                 @Nullable CommandFeedbackScope feedback) {
        registeredOwners.add(owner);
        return new CommandRegistration() {
            private boolean closed;

            @Override
            public @NotNull CommandDefinition getCommand() {
                return command;
            }

            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public void close() {
                closed = true;
            }
        };
    }

    @Override
    public void unregisterOwner(@NotNull Object owner) {
        unregisteredOwners.add(owner);
    }
}
