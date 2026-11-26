package org.pnfolder.pnlibrary.core.settings;

import org.jetbrains.annotations.NotNull;

/** A value that cannot be accepted for a setting; the message says why, in words for the administrator. */
public final class SettingException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** Creates the exception with the reason. */
    public SettingException(@NotNull String message) {
        super(message);
    }
}
