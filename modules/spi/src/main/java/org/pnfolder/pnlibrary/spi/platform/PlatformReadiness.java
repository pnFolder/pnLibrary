package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;

/** Server readiness scheduling boundary for work that must wait until native startup completes. */
public interface PlatformReadiness {
    /** Runs {@code task} once native startup has completed. */
    default void whenServerReady(@NotNull Runnable task) {
    }
}
