package org.pnfolder.pnlibrary.api.placeholders.info;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** A player written in an address: a name or a UUID. Looking the name up is left to the plugin. */
public final class PlayerRef {
    private final String text;
    private final UUID uuid;

    /** Wraps the text written in the address. */
    public PlayerRef(@NotNull String text) {
        this.text = Objects.requireNonNull(text);
        this.uuid = parse(text);
    }

    /** The text as written. */
    @NotNull
    public String getText() {
        return text;
    }

    /** The UUID when {@link #getText()} is one, otherwise {@code null}. */
    @Nullable
    public UUID getUuid() {
        return uuid;
    }

    @Nullable
    private static UUID parse(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }

    @NotNull
    @Override
    public String toString() {
        return text;
    }
}
