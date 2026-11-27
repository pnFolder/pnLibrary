package org.pnfolder.pnlibrary.core.menu.session;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A click a viewer made on a menu slot. */
public final class MenuClick {
    private final MenuViewer player;
    private final int slot;
    private final ClickType click;

    public MenuClick(@NotNull MenuViewer player, int slot, @NotNull ClickType click) {
        this.player = Objects.requireNonNull(player, "player");
        this.slot = slot;
        this.click = Objects.requireNonNull(click, "click");
    }

    public @NotNull MenuViewer getPlayer() {
        return player;
    }

    public int getSlot() {
        return slot;
    }

    public @NotNull ClickType getClick() {
        return click;
    }
}
