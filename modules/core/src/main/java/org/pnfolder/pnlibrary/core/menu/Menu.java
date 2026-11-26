package org.pnfolder.pnlibrary.core.menu;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** A finished chest menu that a viewer can open. */
public final class Menu {
    private final String title;
    private final int rows;
    private final Map<Integer, MenuItem> items;

    public Menu(@NotNull String title, int rows, @NotNull Map<Integer, MenuItem> items) {
        this.title = title;
        this.rows = rows;
        this.items = items;
    }

    public @NotNull String getTitle() {
        return title;
    }

    public int getRows() {
        return rows;
    }

    public @NotNull Map<Integer, MenuItem> getItems() {
        return items;
    }
}
