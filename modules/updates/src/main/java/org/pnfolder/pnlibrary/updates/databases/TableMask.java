package org.pnfolder.pnlibrary.updates.databases;

import java.util.List;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/** Picks tables or collections by name: an empty list takes all of them, {@code *} in a name stands for any text ({@code log_*}). */
public final class TableMask {
    private TableMask() {
    }

    /** True when {@code name} is taken by {@code masks}. */
    public static boolean matches(@NotNull List<String> masks, @NotNull String name) {
        if (masks.isEmpty()) {
            return true;
        }
        for (String mask : masks) {
            if (toPattern(mask).matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern toPattern(String mask) {
        StringBuilder regex = new StringBuilder();
        String[] parts = mask.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString());
    }
}
