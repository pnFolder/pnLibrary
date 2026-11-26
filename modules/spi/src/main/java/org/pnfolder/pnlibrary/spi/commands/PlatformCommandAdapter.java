package org.pnfolder.pnlibrary.spi.commands;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

/** Native command registration boundary implemented by each platform runtime. */
public interface PlatformCommandAdapter extends AutoCloseable {
    /** Installs {@code command} for {@code owner} and connects it to {@code dispatcher}. */
    @NotNull
    PlatformCommandRegistration register(
            @NotNull Object owner,
            @NotNull CommandDefinition command,
            @NotNull PlatformCommandDispatcher dispatcher);

    /** Releases every native command resource owned by this adapter. */
    @Override
    default void close() {
    }
}
