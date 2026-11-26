package org.pnfolder.pnlibrary.core.link.remote;

import org.jetbrains.annotations.NotNull;

/** Why a data request failed; its message is shown to the player or logged. */
public final class DataException extends RuntimeException {
    public DataException(@NotNull String message) {
        super(message);
    }
}
