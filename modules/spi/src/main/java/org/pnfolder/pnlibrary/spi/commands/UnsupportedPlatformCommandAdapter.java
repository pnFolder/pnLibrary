package org.pnfolder.pnlibrary.spi.commands;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

/** Default used until a platform installs its native command bridge. */
public final class UnsupportedPlatformCommandAdapter implements PlatformCommandAdapter {
    /** The only instance. */
    public static final UnsupportedPlatformCommandAdapter INSTANCE = new UnsupportedPlatformCommandAdapter();

    private UnsupportedPlatformCommandAdapter() {
    }

    /** Returns a no-op registration because no native bridge is installed. */
    @NotNull
    @Override
    public PlatformCommandRegistration register(
            @NotNull Object owner,
            @NotNull CommandDefinition command,
            @NotNull PlatformCommandDispatcher dispatcher) {
        return () -> { };
    }
}
