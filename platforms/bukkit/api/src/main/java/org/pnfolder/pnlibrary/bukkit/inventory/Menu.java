package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable menu definition reusable across players and sessions.
 *
 * <p>Static {@link #getItems()} are copied into each new inventory before {@link #getRenderer()} runs. The renderer
 * can then replace them with player-specific or current application state.
 */
public final class Menu {
    private final String title;
    private final int rows;
    private final MenuType type;
    private final Map<Integer, MenuItem> items;
    private final MenuRenderer renderer;
    private final MenuOpenHandler openHandler;
    private final MenuCloseHandler closeHandler;
    private final MenuClickHandler clickHandler;
    private final boolean cancelPlayerInventory;

    /**
     * @param title inventory title shown to the player
     * @param rows chest row count; ignored by non-chest menu types
     * @param type inventory type
     * @param items static slot definitions keyed by raw top-inventory slot
     * @param renderer optional dynamic renderer invoked on open and explicit refresh
     * @param openHandler optional callback invoked after initial rendering and opening
     * @param closeHandler optional callback for normal closes while the owner remains enabled
     * @param clickHandler optional handler invoked after a clicked slot's own handler
     * @param cancelPlayerInventory whether clicks in the player's lower inventory are cancelled
     */
    public Menu(@NotNull String title, int rows, @NotNull MenuType type, @NotNull Map<Integer, MenuItem> items,
                @Nullable MenuRenderer renderer, @Nullable MenuOpenHandler openHandler,
                @Nullable MenuCloseHandler closeHandler, @Nullable MenuClickHandler clickHandler,
                boolean cancelPlayerInventory) {
        this.title = Objects.requireNonNull(title, "title");
        this.rows = rows;
        this.type = Objects.requireNonNull(type, "type");
        this.items = Objects.requireNonNull(items, "items");
        this.renderer = renderer;
        this.openHandler = openHandler;
        this.closeHandler = closeHandler;
        this.clickHandler = clickHandler;
        this.cancelPlayerInventory = cancelPlayerInventory;
    }

    @NotNull
    public String getTitle() {
        return title;
    }

    public int getRows() {
        return rows;
    }

    @NotNull
    public MenuType getType() {
        return type;
    }

    @NotNull
    public Map<Integer, MenuItem> getItems() {
        return items;
    }

    @Nullable
    public MenuRenderer getRenderer() {
        return renderer;
    }

    @Nullable
    public MenuOpenHandler getOpenHandler() {
        return openHandler;
    }

    @Nullable
    public MenuCloseHandler getCloseHandler() {
        return closeHandler;
    }

    @Nullable
    public MenuClickHandler getClickHandler() {
        return clickHandler;
    }

    public boolean isCancelPlayerInventory() {
        return cancelPlayerInventory;
    }

    /** Total slot count of the top inventory. */
    public int getSize() {
        return type == MenuType.CHEST ? rows * 9 : type.getDefaultSize();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Menu)) {
            return false;
        }
        Menu that = (Menu) other;
        return rows == that.rows && cancelPlayerInventory == that.cancelPlayerInventory
            && title.equals(that.title) && type == that.type && items.equals(that.items)
            && Objects.equals(renderer, that.renderer) && Objects.equals(openHandler, that.openHandler)
            && Objects.equals(closeHandler, that.closeHandler) && Objects.equals(clickHandler, that.clickHandler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, rows, type, items, renderer, openHandler, closeHandler, clickHandler,
            cancelPlayerInventory);
    }

    @Override
    public String toString() {
        return "Menu(title=" + title + ", rows=" + rows + ", type=" + type + ", items=" + items
            + ", cancelPlayerInventory=" + cancelPlayerInventory + ")";
    }
}
