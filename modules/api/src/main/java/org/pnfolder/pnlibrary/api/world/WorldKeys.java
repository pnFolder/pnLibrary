package org.pnfolder.pnlibrary.api.world;

import java.util.Locale;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/** Turns text from configs and scenarios into keys. */
public final class WorldKeys {
    private WorldKeys() {
    }

    /**
     * Turns text written in a config or a scenario into a {@link Key}: {@code speed} and {@code SPEED} both give
     * {@code minecraft:speed}, and {@code shop:coin} keeps its namespace. Throws when the text cannot be a key.
     */
    public static @NotNull Key minecraftKey(@NotNull String text) {
        return Key.key(text.trim().toLowerCase(Locale.ROOT));
    }
}
