package org.pnfolder.pnlibrary.core.settings;

import org.jetbrains.annotations.NotNull;

/** What kind of value a setting holds; it decides how the value is typed, checked and shown. */
public enum SettingKind {
    /** On or off. */
    SWITCH("вкл/выкл"),
    /** A whole number. */
    NUMBER("число"),
    /** A line of text. */
    TEXT("текст"),
    /** A length of time. */
    TIME("время"),
    /** One value of a list. */
    CHOICE("выбор из списка");

    private final String label;

    SettingKind(String label) {
        this.label = label;
    }

    /** The name of the kind for the administrator. */
    public @NotNull String getLabel() {
        return label;
    }
}
