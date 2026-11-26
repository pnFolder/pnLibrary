package org.pnfolder.pnlibrary.core.menu;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.Link;

/** What a menu needs from the platform: threads, scheduling and plugin lookup. */
public interface MenuHost extends PluginDirectory {
    void sync(@NotNull Runnable action);

    void later(long delayMillis, @NotNull Runnable action);

    /** Runs {@code action} away from that thread, where waiting for another server is allowed. */
    void async(@NotNull Runnable action);

    /** Opens the menu of the server called {@code target} for {@code viewer}; false when that is not possible from here. */
    boolean openRemote(@NotNull MenuViewer viewer, @NotNull String target);

    default @NotNull Link getLink() {
        return Link.OFF;
    }
}
