package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Immutable initial definition and click behavior for one menu slot. */
public final class MenuItem {
    private final ItemStack item;
    private final MenuClickHandler handler;
    private final boolean editable;

    /**
     * @param item cloned initial item, or {@code null} for an empty slot
     * @param handler slot-specific click handler, or {@code null}
     * @param editable whether Bukkit may apply player inventory actions to this slot
     */
    public MenuItem(@Nullable ItemStack item, @Nullable MenuClickHandler handler, boolean editable) {
        this.item = item;
        this.handler = handler;
        this.editable = editable;
    }

    @Nullable
    public ItemStack getItem() {
        return item;
    }

    @Nullable
    public MenuClickHandler getHandler() {
        return handler;
    }

    public boolean isEditable() {
        return editable;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MenuItem)) {
            return false;
        }
        MenuItem that = (MenuItem) other;
        return editable == that.editable && Objects.equals(item, that.item) && Objects.equals(handler, that.handler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, handler, editable);
    }

    @Override
    public String toString() {
        return "MenuItem(item=" + item + ", handler=" + handler + ", editable=" + editable + ")";
    }
}
