package org.pnfolder.pnlibrary.core.menu;

import org.jetbrains.annotations.NotNull;

/** Reacts to a click on a menu button. */
@FunctionalInterface
public interface MenuClickHandler {
    void handle(@NotNull MenuClick click);
}
