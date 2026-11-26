package org.pnfolder.pnlibrary.core.settings;

import org.jetbrains.annotations.NotNull;

/** When a changed value starts to work. */
public enum SettingApply {
    /** At once. */
    LIVE("сразу"),
    /** After the server restarts. */
    RESTART("после перезапуска");

    private final String label;

    SettingApply(String label) {
        this.label = label;
    }

    /** The moment in words for the administrator. */
    public @NotNull String getLabel() {
        return label;
    }
}
