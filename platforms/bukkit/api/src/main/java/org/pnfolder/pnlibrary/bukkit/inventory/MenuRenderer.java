package org.pnfolder.pnlibrary.bukkit.inventory;

import org.jetbrains.annotations.NotNull;

/** Rebuilds dynamic inventory contents for a menu session. */
@FunctionalInterface
public interface MenuRenderer {

    /** Renders current application state into {@code session} through {@link MenuSession#set}. */
    void render(@NotNull MenuSession session);
}
