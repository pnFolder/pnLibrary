package org.pnfolder.pnlibrary.core.menu;

import org.jetbrains.annotations.NotNull;

/** Entry point for building menus. */
public final class Menus {
    private Menus() {
    }

    public static @NotNull MenuBuilder chest(@NotNull String title) {
        return new MenuBuilder(title);
    }
}
