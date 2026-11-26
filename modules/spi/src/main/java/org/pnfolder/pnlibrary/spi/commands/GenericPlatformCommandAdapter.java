package org.pnfolder.pnlibrary.spi.commands;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

/** Connects one native source type to the shared portable command dispatcher. */
public abstract class GenericPlatformCommandAdapter<S> extends TrackingPlatformCommandAdapter {
    /** Native registration bridge of the platform. */
    @NotNull
    protected abstract NativeCommandRegistrar<S> getRegistrar();

    /** Builds the portable command context for one native invocation. */
    @NotNull
    protected abstract CommandContext context(@NotNull S source, @NotNull String alias, @NotNull String[] arguments);

    @NotNull
    @Override
    protected final PlatformCommandRegistration registerNative(
            @NotNull Object owner,
            @NotNull CommandDefinition command,
            @NotNull PlatformCommandDispatcher dispatcher) {
        return getRegistrar().register(
                owner,
                command,
                (source, alias, arguments) -> dispatcher.execute(command, context(source, alias, arguments)),
                (source, alias, arguments) -> dispatcher.suggest(command, context(source, alias, arguments)));
    }
}
