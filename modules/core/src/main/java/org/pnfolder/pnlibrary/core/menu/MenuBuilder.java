package org.pnfolder.pnlibrary.core.menu;

import org.pnfolder.pnlibrary.core.menu.session.MenuClickHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Collects slots of a chest menu; create it with {@link Menus#chest}. */
public final class MenuBuilder {
    private static final int DEFAULT_ROWS = 6;
    private static final int MAX_ROWS = 6;
    private static final int SLOTS_PER_ROW = 9;

    private final String title;
    private int rows = DEFAULT_ROWS;
    private final Map<Integer, MenuItem> items = new LinkedHashMap<>();

    MenuBuilder(String title) {
        this.title = title;
    }

    public @NotNull MenuBuilder rows(int value) {
        if (value < 1 || value > MAX_ROWS) {
            throw new IllegalArgumentException("Menu rows must be between 1 and 6");
        }
        rows = value;
        return this;
    }

    public @NotNull MenuBuilder slot(int index, @Nullable ItemSpec item) {
        return slot(index, item, null);
    }

    public @NotNull MenuBuilder slot(int index, @Nullable ItemSpec item, @Nullable MenuClickHandler click) {
        if (index < 0 || index >= rows * SLOTS_PER_ROW) {
            throw new IllegalArgumentException("Slot " + index + " is outside inventory");
        }
        items.put(index, new MenuItem(item, click));
        return this;
    }

    public @NotNull MenuBuilder button(int index, @NotNull ItemSpec item, @NotNull MenuClickHandler click) {
        return slot(index, item, click);
    }

    public @NotNull MenuBuilder fill(@NotNull ItemSpec item) {
        return fill(item, 0);
    }

    public @NotNull MenuBuilder fill(@NotNull ItemSpec item, int from) {
        return fill(item, from, rows * SLOTS_PER_ROW);
    }

    /** Puts {@code item} into every empty slot from {@code from} up to, not including, {@code until}. */
    public @NotNull MenuBuilder fill(@NotNull ItemSpec item, int from, int until) {
        for (int slot = from; slot < until; slot++) {
            if (!items.containsKey(slot)) {
                items.put(slot, new MenuItem(item, null));
            }
        }
        return this;
    }

    public @NotNull Menu build() {
        return new Menu(title, rows, new LinkedHashMap<>(items));
    }
}
