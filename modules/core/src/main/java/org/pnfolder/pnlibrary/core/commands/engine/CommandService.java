package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

/**
 * Registers portable commands and binds their lifecycle to an owner.
 * Registration, dispatch lookup, owner removal, and shutdown are safe from arbitrary threads.
 * Command callbacks execute in the context selected by the platform dispatcher.
 */
public interface CommandService {
    /**
     * Registers {@code command} and binds its lifecycle to {@code owner}; {@code feedback} holds what the owning module
     * wants every one of its commands to answer when a sender is denied or types the command wrongly.
     */
    @NotNull
    CommandRegistration register(@NotNull Object owner, @NotNull CommandDefinition command, @Nullable CommandFeedbackScope feedback);

    /** Registers {@code command} without module-level feedback. */
    @NotNull
    default CommandRegistration register(@NotNull Object owner, @NotNull CommandDefinition command) {
        return register(owner, command, null);
    }

    /** Builds and registers a command so the call site stays free of the command-definition ceremony. */
    @NotNull
    default CommandRegistration register(@NotNull Object owner, @NotNull String name, @NotNull Consumer<CommandBuilder> configure) {
        return register(owner, CommandDefinition.command(name, configure));
    }

    void unregisterOwner(@NotNull Object owner);
}
