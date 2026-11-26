package org.pnfolder.pnlibrary.core.menu.style;

import org.jetbrains.annotations.NotNull;

/** Gradient color pair used for titles and highlights. */
public enum Tone {
    NORMAL("D19BFF", "F5C6FF"),
    GOOD("6EE7A0", "B8F5D2"),
    WARN("FDE047", "FFB347"),
    DANGER("FF7A7A", "FFB0A0"),
    MUTED("9AA4AD", "C9D0D6");

    private final String from;
    private final String to;

    Tone(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public @NotNull String getFrom() {
        return from;
    }

    public @NotNull String getTo() {
        return to;
    }

    /** The tone that wins when several apply: danger, then warn, good and muted; normal when none does. */
    public static @NotNull Tone of(boolean danger, boolean warn, boolean good, boolean muted) {
        if (danger) {
            return DANGER;
        }
        if (warn) {
            return WARN;
        }
        if (good) {
            return GOOD;
        }
        return muted ? MUTED : NORMAL;
    }
}
