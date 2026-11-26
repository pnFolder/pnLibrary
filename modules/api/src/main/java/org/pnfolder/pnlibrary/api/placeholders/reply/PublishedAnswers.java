package org.pnfolder.pnlibrary.api.placeholders.reply;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** How a published prefix is answered: the text after the prefix goes in, the value (or {@code null}) comes out. */
@FunctionalInterface
public interface PublishedAnswers {
    /**
     * Answers an outside request.
     *
     * @param params the text of the outside request without the prefix
     * @param waitMillis how long to wait for a late value (0: not at all)
     */
    @Nullable
    String answer(@Nullable UUID playerId, @NotNull String params, long waitMillis);
}
