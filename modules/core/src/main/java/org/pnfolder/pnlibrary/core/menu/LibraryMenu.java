package org.pnfolder.pnlibrary.core.menu;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.menu.data.MenuData;

/**
 * The inventory menu of {@code /pn gui}: everything the update commands do, on several connected screens.
 *
 * <p>Screens: the home page, the list of plugins (all, with updates, with problems), the card of one plugin, its
 * channel, pause and releases, the history of one plugin or of all and one entry of it, the saved backups with a
 * rollback choice, the journal with one event, the tasks of every plugin, and the plugin events (event bus) with
 * their listeners, days and recorded calls.
 * Every action calls the same services as the commands and asks for the same permissions, so the audit record is the
 * same, and every answer is a chat notice in the library's own style. Buttons are textured heads with an ordinary
 * item as the fallback, so the menu opens on every server from 1.8.
 */
public final class LibraryMenu {
    private final MenuRouter router;

    public LibraryMenu(@NotNull MenuData data, @NotNull MenuHost host) {
        this(data, host, null);
    }

    public LibraryMenu(@NotNull MenuData data, @NotNull MenuHost host, @Nullable String remoteName) {
        this.router = new MenuRouter(new MenuSession(data, host, remoteName));
    }

    public void home(@NotNull MenuViewer player) {
        router.home(player);
    }

    public void open(@NotNull MenuViewer player) {
        router.open(player);
    }

    /** Opens the list of values of the choice setting {@code key}, only those that contain {@code filter}; for {@code /pn settings pick}. */
    public void pickSetting(@NotNull MenuViewer player, @NotNull String key, @NotNull String filter) {
        router.pick(player, key, filter);
    }
}
