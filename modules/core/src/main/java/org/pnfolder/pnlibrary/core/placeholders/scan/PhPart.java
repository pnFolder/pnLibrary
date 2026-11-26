package org.pnfolder.pnlibrary.core.placeholders.scan;

import org.pnfolder.pnlibrary.core.placeholders.SlotType;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/** One part of the path of a placeholder: a fixed word, or a slot the address fills in. */
public final class PhPart {
    private static final Pattern WORD = Pattern.compile("[a-z0-9_-]+");

    private final String name;
    private final SlotType type;

    private PhPart(String name, SlotType type) {
        this.name = name;
        this.type = type;
    }

    public static PhPart word(String name) {
        return new PhPart(name, null);
    }

    static PhPart slot(String name, SlotType type) {
        return new PhPart(name, type);
    }

    /** A word of a path: lower case letters, digits, {@code _} and {@code -}. */
    public static @NotNull String checkWord(@NotNull String name) {
        if (!WORD.matcher(name).matches()) {
            throw new IllegalArgumentException("Placeholder part '" + name + "' must use lowercase letters, digits, '_' or '-'");
        }
        return name;
    }

    public String getName() {
        return name;
    }

    public boolean isSlot() {
        return type != null;
    }

    public SlotType getType() {
        return type;
    }
}
