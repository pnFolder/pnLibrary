package org.pnfolder.pnlibrary.bukkit.inventory;

import org.jetbrains.annotations.NotNull;

/** Receives a normal player-driven menu close notification. */
@FunctionalInterface
public interface MenuCloseHandler {

    /** Processes {@code close} after the session has been removed from the service. */
    void handle(@NotNull MenuClose close);
}
