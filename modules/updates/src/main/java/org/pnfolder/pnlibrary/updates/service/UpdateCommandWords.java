package org.pnfolder.pnlibrary.updates.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Words that follow {@code /pn update} and mean a step of the update flow, not a plugin.
 *
 * <p>{@code /pn update <plugin>} updates a plugin, {@code /pn update confirm <token>} carries out a confirmation,
 * and so on. The command takes the word as a step first, so a plugin named like one of them could never be updated
 * by name. Such a name is refused when the plugin registers its updates.
 */
public final class UpdateCommandWords {
    /** The reserved words, in lower case. */
    @NotNull
    public static final Set<String> RESERVED = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("confirm", "status", "rollback", "restart", "all")));

    private UpdateCommandWords() {
    }

    /** Whether {@code name} is one of the reserved words, ignoring case. */
    public static boolean isReserved(@NotNull String name) {
        return RESERVED.contains(name.trim().toLowerCase(Locale.ROOT));
    }
}
