package org.pnfolder.pnlibrary.api.commands.error;

import org.jetbrains.annotations.Nullable;

/** The base of everything else that went wrong, and the last resort of the standard answers. */
public class CommandFailed extends CommandError {
    /** A failure with the standard text. */
    public CommandFailed() {
        super();
    }

    /** A failure with its own default text; see {@link CommandError#CommandError(String)}. */
    public CommandFailed(@Nullable String defaultMessage) {
        super(defaultMessage);
    }
}
