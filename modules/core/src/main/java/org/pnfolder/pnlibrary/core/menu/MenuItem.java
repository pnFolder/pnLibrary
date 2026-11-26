package org.pnfolder.pnlibrary.core.menu;

import org.jetbrains.annotations.Nullable;

/** A slot of a menu: the item shown and the handler for clicks, either may be null. */
public final class MenuItem {
    private final ItemSpec item;
    private final MenuClickHandler handler;

    public MenuItem(@Nullable ItemSpec item, @Nullable MenuClickHandler handler) {
        this.item = item;
        this.handler = handler;
    }

    public @Nullable ItemSpec getItem() {
        return item;
    }

    public @Nullable MenuClickHandler getHandler() {
        return handler;
    }
}
