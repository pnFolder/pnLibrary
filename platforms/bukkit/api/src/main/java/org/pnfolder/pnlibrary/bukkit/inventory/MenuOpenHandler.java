package org.pnfolder.pnlibrary.bukkit.inventory;

import org.jetbrains.annotations.NotNull;

/** Receives a session after its inventory has opened and initial rendering has completed. */
@FunctionalInterface
public interface MenuOpenHandler {

    /** Processes the newly opened {@code session}. */
    void handle(@NotNull MenuSession session);
}
