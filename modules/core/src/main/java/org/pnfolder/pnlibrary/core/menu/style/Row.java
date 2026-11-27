package org.pnfolder.pnlibrary.core.menu.style;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.session.MenuClickHandler;

/** One menu row: an item and an optional click handler. */
public final class Row {
    private final ItemSpec stack;
    private final MenuClickHandler click;

    public Row(@NotNull ItemSpec stack, @Nullable MenuClickHandler click) {
        this.stack = Objects.requireNonNull(stack, "stack");
        this.click = click;
    }

    public Row(@NotNull ItemSpec stack) {
        this(stack, null);
    }

    public @NotNull ItemSpec getStack() {
        return stack;
    }

    public @Nullable MenuClickHandler getClick() {
        return click;
    }
}
