package org.pnfolder.pnlibrary.core.placeholders;

import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/** One part of the path of a placeholder: a fixed word, or a slot the address fills in. */
final class PhPart {
    private static final Pattern WORD = Pattern.compile("[a-z0-9_-]+");

    private final String name;
    private final SlotType type;

    private PhPart(String name, SlotType type) {
        this.name = name;
        this.type = type;
    }

    static PhPart word(String name) {
        return new PhPart(name, null);
    }

    static PhPart slot(String name, SlotType type) {
        return new PhPart(name, type);
    }

    /** A word of a path: lower case letters, digits, {@code _} and {@code -}. */
    static @NotNull String checkWord(@NotNull String name) {
        if (!WORD.matcher(name).matches()) {
            throw new IllegalArgumentException("Placeholder part '" + name + "' must use lowercase letters, digits, '_' or '-'");
        }
        return name;
    }

    String getName() {
        return name;
    }

    boolean isSlot() {
        return type != null;
    }

    SlotType getType() {
        return type;
    }
}
