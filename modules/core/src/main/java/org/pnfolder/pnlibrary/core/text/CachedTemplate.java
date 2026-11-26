package org.pnfolder.pnlibrary.core.text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.text.ComponentTemplate;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializer;

/** A component source rendered through placeholders, then parsed with one serializer. */
final class CachedTemplate implements ComponentTemplate {
    private final PlaceholderService placeholders;
    private final String source;
    private final ComponentSerializer serializer;
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();
    private UUID playerId;

    CachedTemplate(PlaceholderService placeholders, String source, ComponentSerializer serializer) {
        this.placeholders = placeholders;
        this.source = source;
        this.serializer = serializer;
    }

    @Override
    public @NotNull ComponentTemplate value(@NotNull String name, @Nullable Object value) {
        values.put(name, value);
        return this;
    }

    @Override
    public @NotNull ComponentTemplate player(@NotNull UUID playerId) {
        this.playerId = playerId;
        return this;
    }

    @Override
    public @NotNull CompletionStage<Component> renderAsync() {
        return placeholders.render(source, playerId, values).thenApply(serializer::deserialize);
    }
}
