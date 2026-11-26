package org.pnfolder.pnlibrary.api.commands.error;

import org.jetbrains.annotations.NotNull;

/** The command stopped before a part it needs. */
public class IncompleteCommand extends BadUsage {
    /** @param usage generated hint, such as {@code /shop buy <item>} */
    public IncompleteCommand(@NotNull String usage) {
        super(usage);
    }
}
