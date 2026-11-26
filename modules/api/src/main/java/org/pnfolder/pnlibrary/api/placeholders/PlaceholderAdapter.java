package org.pnfolder.pnlibrary.api.placeholders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;

import java.util.UUID;

/** Bridge between the placeholders of the library and one outside placeholder system, such as PlaceholderAPI. */
public interface PlaceholderAdapter {
    /** Stable, case-insensitive identifier. */
    @NotNull
    String getId();

    /** Whether the outside system is there to talk to right now. */
    boolean isAvailable();

    /** The value the outside system gives for {@code expression}, or {@code null}. */
    @Nullable
    default Object resolve(@Nullable UUID playerId, @NotNull String expression) {
        return null;
    }

    /** Replaces the tokens the outside system owns in {@code text} (for PlaceholderAPI, every {@code %...%}); unknown ones stay. */
    @NotNull
    default String render(@Nullable UUID playerId, @NotNull String text) {
        return text;
    }

    /** Shows everything under {@code prefix} to the outside system, answering with {@code answers}. Close the handle to withdraw it. */
    @NotNull
    AutoCloseable publish(@NotNull String prefix, @NotNull PublishedAnswers answers);
}
