package org.pnfolder.pnlibrary.bukkit.inventory;

import org.jetbrains.annotations.NotNull;

/** Entry points for each supported menu shape. */
public final class Menus {
    private Menus() {
    }

    /** Creates a six-row chest builder; call {@link MenuBuilder#rows} to resize it. */
    @NotNull
    public static MenuBuilder chest(@NotNull String title) {
        return new MenuBuilder(title, MenuType.CHEST);
    }

    @NotNull
    public static MenuBuilder anvil(@NotNull String title) {
        return new MenuBuilder(title, MenuType.ANVIL);
    }

    @NotNull
    public static MenuBuilder hopper(@NotNull String title) {
        return new MenuBuilder(title, MenuType.HOPPER);
    }

    @NotNull
    public static MenuBuilder dispenser(@NotNull String title) {
        return new MenuBuilder(title, MenuType.DISPENSER);
    }

    @NotNull
    public static MenuBuilder dropper(@NotNull String title) {
        return new MenuBuilder(title, MenuType.DROPPER);
    }

    @NotNull
    public static MenuBuilder workbench(@NotNull String title) {
        return new MenuBuilder(title, MenuType.WORKBENCH);
    }
}
