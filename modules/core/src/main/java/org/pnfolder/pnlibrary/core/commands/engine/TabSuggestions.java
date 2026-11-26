package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/** Waiting for tab suggestions on a thread that must not stall. */
public final class TabSuggestions {
    /** How long the tick loop may wait for tab suggestions. */
    public static final long MAIN_THREAD_SUGGESTION_MILLIS = 50L;

    /** How long a proxy thread, which is not the tick loop, may wait for tab suggestions. */
    public static final long PROXY_SUGGESTION_MILLIS = 1000L;

    private TabSuggestions() {
    }

    /**
     * Waits for tab suggestions for at most {@code millis} and returns an empty list when they are late or failed.
     * A slow suggestion provider costs the player one empty Tab instead of a lagging tick or an error in the log.
     */
    @NotNull
    public static List<String> suggestionsWithin(@NotNull CompletionStage<List<String>> stage, long millis) {
        try {
            return stage.toCompletableFuture().get(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        } catch (Exception late) {
            return Collections.emptyList();
        }
    }
}
