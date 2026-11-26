package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable details of one top-inventory click.
 *
 * <p>A slot-specific {@link MenuItem#getHandler()} runs before {@link Menu#getClickHandler()}. Non-editable slots
 * are cancelled before either handler runs. Item stacks reflect the Bukkit event and should be cloned by handlers
 * that retain or mutate them asynchronously.
 */
public final class MenuClick {
    private final MenuSession session;
    private final int slot;
    private final ItemStack item;
    private final ItemStack cursor;
    private final ClickType click;
    private final InventoryAction action;
    private final boolean topInventory;
    private final String renameText;

    /**
     * @param session active session receiving the event
     * @param slot raw zero-based slot in the top inventory
     * @param item item occupying the clicked slot, if any
     * @param cursor item currently held by the cursor, if any
     * @param click physical Bukkit click type
     * @param action inventory action inferred by Bukkit
     * @param topInventory always true for dispatched menu clicks
     * @param renameText current nonblank anvil input when available
     */
    public MenuClick(@NotNull MenuSession session, int slot, @Nullable ItemStack item, @Nullable ItemStack cursor,
                     @NotNull ClickType click, @NotNull InventoryAction action, boolean topInventory,
                     @Nullable String renameText) {
        this.session = Objects.requireNonNull(session, "session");
        this.slot = slot;
        this.item = item;
        this.cursor = cursor;
        this.click = Objects.requireNonNull(click, "click");
        this.action = Objects.requireNonNull(action, "action");
        this.topInventory = topInventory;
        this.renameText = renameText;
    }

    /** Same as the full constructor with no rename text. */
    public MenuClick(@NotNull MenuSession session, int slot, @Nullable ItemStack item, @Nullable ItemStack cursor,
                     @NotNull ClickType click, @NotNull InventoryAction action, boolean topInventory) {
        this(session, slot, item, cursor, click, action, topInventory, null);
    }

    @NotNull
    public MenuSession getSession() {
        return session;
    }

    public int getSlot() {
        return slot;
    }

    @Nullable
    public ItemStack getItem() {
        return item;
    }

    @Nullable
    public ItemStack getCursor() {
        return cursor;
    }

    @NotNull
    public ClickType getClick() {
        return click;
    }

    @NotNull
    public InventoryAction getAction() {
        return action;
    }

    public boolean isTopInventory() {
        return topInventory;
    }

    @Nullable
    public String getRenameText() {
        return renameText;
    }

    /** The player who clicked. */
    @NotNull
    public Player getPlayer() {
        return session.getPlayer();
    }

    /** Closes the menu for the clicking player. */
    public void close() {
        session.close();
    }

    /** Immediately invokes the configured renderer when the session is still active. */
    public void refresh() {
        session.refresh();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MenuClick)) {
            return false;
        }
        MenuClick that = (MenuClick) other;
        return slot == that.slot && topInventory == that.topInventory && session.equals(that.session)
            && Objects.equals(item, that.item) && Objects.equals(cursor, that.cursor)
            && click == that.click && action == that.action && Objects.equals(renameText, that.renameText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(session, slot, item, cursor, click, action, topInventory, renameText);
    }

    @Override
    public String toString() {
        return "MenuClick(slot=" + slot + ", click=" + click + ", action=" + action + ", item=" + item
            + ", cursor=" + cursor + ", topInventory=" + topInventory + ", renameText=" + renameText + ")";
    }
}
