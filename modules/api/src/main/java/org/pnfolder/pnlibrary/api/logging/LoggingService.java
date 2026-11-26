package org.pnfolder.pnlibrary.api.logging;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Creates plugin-bound loggers and console message boxes. */
@ApiStatus.NonExtendable
public interface LoggingService {
    /** Returns the logger called {@code name} that belongs to the plugin {@code owner}. */
    @NotNull PnLogger logger(@NotNull Object owner, @NotNull String name);

    /** Starts a startup summary block titled {@code title} for {@code owner}; nothing is printed until {@code show()}. */
    @NotNull MessageBox box(@NotNull Object owner, @NotNull String title);

    /** Starts a shutdown summary block titled {@code title} for {@code owner}. */
    @NotNull MessageBox shutdownBox(@NotNull Object owner, @NotNull String title);

    /** Same as {@link #box(Object, String)} with the title {@code name version}. */
    default @NotNull MessageBox box(@NotNull Object owner, @NotNull String name, @NotNull String version) {
        return box(owner, name + " " + version);
    }

    /** Same as {@link #shutdownBox(Object, String)} with the title {@code name version}. */
    default @NotNull MessageBox shutdownBox(@NotNull Object owner, @NotNull String name, @NotNull String version) {
        return shutdownBox(owner, name + " " + version);
    }
}
