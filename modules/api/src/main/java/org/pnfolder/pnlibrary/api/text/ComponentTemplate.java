package org.pnfolder.pnlibrary.api.text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Mutable rendering invocation created from one serialized component template. */
public interface ComponentTemplate {
    /** Associates a named template token with {@code value}. */
    @NotNull ComponentTemplate value(@NotNull String name, @Nullable Object value);

    /** Associates every entry of {@code values} with its named template token. */
    default @NotNull ComponentTemplate values(@NotNull Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            value(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /** Selects the player used by player-aware placeholder resolution. */
    @NotNull ComponentTemplate player(@NotNull UUID playerId);

    /** Resolves configured values and placeholders into the final component without blocking the caller. */
    @NotNull CompletionStage<Component> renderAsync();

    /** Like {@link #renderAsync()}, but waits for the result; do not call it from a thread that placeholders may need. */
    default @NotNull Component render() {
        return renderAsync().toCompletableFuture().join();
    }
}
