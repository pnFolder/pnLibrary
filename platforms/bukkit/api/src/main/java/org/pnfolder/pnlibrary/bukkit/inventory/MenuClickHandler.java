package org.pnfolder.pnlibrary.bukkit.inventory;

import org.jetbrains.annotations.NotNull;

/** Handles one click inside a menu's top inventory. */
@FunctionalInterface
public interface MenuClickHandler {

    /** Processes immutable click details and may update or close the session. */
    void handle(@NotNull MenuClick click);
}
