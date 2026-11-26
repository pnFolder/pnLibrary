package org.pnfolder.pnlibrary.core.settings;

import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandContext;

/**
 * The link between {@code /pn settings pick} and the inventory menu: a platform that has menus sets the opener, the
 * command calls it. A platform without menus (a proxy) leaves it empty, and the command says so.
 */
public final class SettingPickerHook {
    /** Opens the value list of one setting for the player who ran the command. */
    public interface Opener {
        /** Returns false when the list cannot be opened. */
        boolean open(CommandContext context, String key, String filter);
    }

    private volatile Opener opener;

    /**
     * The function that opens the list of values of the setting {@code key}, narrowed to {@code filter}, for the player
     * who ran the command; it returns false when it cannot. Null when the platform has no menus.
     */
    public @Nullable Opener getOpener() {
        return opener;
    }

    /** Sets or clears the opener. */
    public void setOpener(@Nullable Opener opener) {
        this.opener = opener;
    }
}
