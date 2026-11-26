package org.pnfolder.pnlibrary.spi.commands;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

/** Native source-specific registration boundary shared by every platform runtime. */
public interface NativeCommandRegistrar<S> {
    /**
     * Registers {@code command} for its native {@code owner} and returns a handle that unregisters it.
     * Callbacks receive the native source, invoked alias, and arguments in that order.
     * Platforms without alias information may pass the canonical command name.
     */
    @NotNull
    PlatformCommandRegistration register(
            @NotNull Object owner,
            @NotNull CommandDefinition command,
            @NotNull NativeCommandExecutor<S> execute,
            @NotNull NativeCommandSuggester<S> suggest);
}
