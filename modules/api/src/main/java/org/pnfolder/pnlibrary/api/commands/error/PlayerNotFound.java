package org.pnfolder.pnlibrary.api.commands.error;

import org.jetbrains.annotations.NotNull;

/** A player argument that names nobody online. */
public class PlayerNotFound extends InvalidArgument {
    /** See {@link InvalidArgument#InvalidArgument(String, String, String)}. */
    public PlayerNotFound(@NotNull String usage, @NotNull String argument, @NotNull String value) {
        super(usage, argument, value);
    }
}
