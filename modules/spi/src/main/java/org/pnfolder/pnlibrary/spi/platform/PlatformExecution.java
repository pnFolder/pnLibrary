package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;

/** Lifecycle and thread-dispatch operations supplied by a native platform. */
public interface PlatformExecution {
    /** Runs {@code task} on the platform's global (main) execution context. */
    void executeGlobal(@NotNull Runnable task);

    /** Runs {@code task} on the context that owns {@code recipient}, for example the region of a player. */
    void executeReply(@NotNull Object recipient, @NotNull Runnable task);
}
