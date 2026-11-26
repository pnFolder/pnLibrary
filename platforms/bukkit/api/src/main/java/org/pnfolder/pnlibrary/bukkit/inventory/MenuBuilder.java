package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fluent builder for immutable {@link Menu} definitions. */
public final class MenuBuilder {
    private final String title;
    private final MenuType type;
    private final Map<Integer, MenuItem> items = new LinkedHashMap<>();
    private int rows = 6;
    private MenuRenderer renderer;
    private MenuOpenHandler openHandler;
    private MenuCloseHandler closeHandler;
    private MenuClickHandler clickHandler;
    private boolean cancelPlayerInventory = true;

    MenuBuilder(@NotNull String title, @NotNull MenuType type) {
        this.title = title;
        this.type = type;
    }

    /** Sets chest rows from one through six; ignored by fixed-size menu types. */
    @NotNull
    public MenuBuilder rows(int value) {
        if (value < 1 || value > 6) {
            throw new IllegalArgumentException("Menu rows must be between 1 and 6");
        }
        rows = value;
        return this;
    }

    /** Controls whether clicks in the player's lower inventory are cancelled. */
    @NotNull
    public MenuBuilder cancelPlayerInventory(boolean value) {
        cancelPlayerInventory = value;
        return this;
    }

    /** Defines one validated slot, cloning {@code item} to isolate the menu definition. */
    @NotNull
    public MenuBuilder slot(int index, @Nullable ItemStack item, boolean editable, @Nullable MenuClickHandler click) {
        if (index < 0 || index >= effectiveSize()) {
            throw new IllegalArgumentException("Slot " + index + " is outside inventory");
        }
        items.put(index, new MenuItem(item == null ? null : item.clone(), click, editable));
        return this;
    }

    /** Defines a non-editable slot with no click handler. */
    @NotNull
    public MenuBuilder slot(int index, @Nullable ItemStack item) {
        return slot(index, item, false, null);
    }

    /** Defines a slot with the given editability and no click handler. */
    @NotNull
    public MenuBuilder slot(int index, @Nullable ItemStack item, boolean editable) {
        return slot(index, item, editable, null);
    }

    /** Defines a non-editable item with a mandatory click handler. */
    @NotNull
    public MenuBuilder button(int index, @NotNull ItemStack item, @NotNull MenuClickHandler click) {
        return slot(index, item, false, click);
    }

    /** Defines an editable slot initially containing {@code item}. */
    @NotNull
    public MenuBuilder editable(int index, @NotNull ItemStack item) {
        return slot(index, item, true, null);
    }

    /** Defines an initially empty editable slot. */
    @NotNull
    public MenuBuilder editable(int index) {
        return slot(index, null, true, null);
    }

    /** Sets the handler invoked after any slot-specific handler. */
    @NotNull
    public MenuBuilder onClick(@NotNull MenuClickHandler handler) {
        clickHandler = handler;
        return this;
    }

    /** Fills undefined slots in {@code [from, until)} with clones of {@code item}. */
    @NotNull
    public MenuBuilder fill(@NotNull ItemStack item, int from, int until) {
        if (from < 0 || until > effectiveSize() || from > until) {
            throw new IllegalArgumentException("Menu fill range [" + from + ", " + until + ") is outside inventory");
        }
        for (int slot = from; slot < until; slot++) {
            fillIfAbsent(slot, item);
        }
        return this;
    }

    /** Fills undefined slots from {@code from} to the end with clones of {@code item}. */
    @NotNull
    public MenuBuilder fill(@NotNull ItemStack item, int from) {
        return fill(item, from, effectiveSize());
    }

    /** Fills every undefined slot with clones of {@code item}. */
    @NotNull
    public MenuBuilder fill(@NotNull ItemStack item) {
        return fill(item, 0, effectiveSize());
    }

    /** Fills undefined outer chest slots; fixed-size non-chest menus are rejected. */
    @NotNull
    public MenuBuilder border(@NotNull ItemStack item) {
        if (type != MenuType.CHEST) {
            throw new IllegalArgumentException("Borders are available for chest menus");
        }
        for (int slot = 0; slot < effectiveSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                fillIfAbsent(slot, item);
            }
        }
        return this;
    }

    /** Sets the function that fills the menu each time it is opened or refreshed. */
    @NotNull
    public MenuBuilder render(@NotNull MenuRenderer renderer) {
        this.renderer = renderer;
        return this;
    }

    /** Sets the callback that runs after the menu is opened for a player. */
    @NotNull
    public MenuBuilder onOpen(@NotNull MenuOpenHandler handler) {
        openHandler = handler;
        return this;
    }

    /** Sets the callback that runs after a player's session ends. */
    @NotNull
    public MenuBuilder onClose(@NotNull MenuCloseHandler handler) {
        closeHandler = handler;
        return this;
    }

    /** Creates an immutable menu and snapshot of all current slot definitions. */
    @NotNull
    public Menu build() {
        return new Menu(title, rows, type, Collections.unmodifiableMap(new LinkedHashMap<>(items)),
            renderer, openHandler, closeHandler, clickHandler, cancelPlayerInventory);
    }

    private void fillIfAbsent(int slot, ItemStack item) {
        if (!items.containsKey(slot)) {
            items.put(slot, new MenuItem(item.clone(), null, false));
        }
    }

    private int effectiveSize() {
        return type == MenuType.CHEST ? rows * 9 : type.getDefaultSize();
    }
}
