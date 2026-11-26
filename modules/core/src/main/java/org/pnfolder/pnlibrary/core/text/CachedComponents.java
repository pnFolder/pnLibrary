package org.pnfolder.pnlibrary.core.text;

import java.util.Objects;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.text.ComponentService;
import org.pnfolder.pnlibrary.api.text.ComponentTemplate;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCachePolicy;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCacheScope;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCacheStatistics;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializer;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;

/** Serializes, deserializes and renders components for one plugin with optional caching. */
public final class CachedComponents implements ComponentService {
    private final PlaceholderService placeholders;
    private final ComponentCache sharedCache;
    private final Map<ComponentSerializerType, ComponentSerializer> serializers =
        new EnumMap<>(ComponentSerializerType.class);
    private final ComponentCache pluginCache = new ComponentCache();
    private ComponentCachePolicy cachePolicy = new ComponentCachePolicy();
    private ComponentSerializerType defaultType = ComponentSerializerType.ADAPTIVE;

    public CachedComponents(@NotNull PlaceholderService placeholders, @NotNull ComponentCache sharedCache,
                            @NotNull MiniText mini, @NotNull Consumer<String> warn) {
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
        this.sharedCache = Objects.requireNonNull(sharedCache, "sharedCache");
        for (ComponentSerializerType type : ComponentSerializerType.values()) {
            serializers.put(type, new TypedSerializer(type, mini, warn));
        }
    }

    @Override
    public @NotNull ComponentSerializerType getDefaultSerializerType() {
        return defaultType;
    }

    @Override
    public void setDefaultSerializerType(@NotNull ComponentSerializerType type) {
        defaultType = type;
    }

    @Override
    public @NotNull ComponentSerializer serializer(@NotNull ComponentSerializerType type) {
        return serializers.get(type);
    }

    @Override
    public @NotNull Component deserialize(@NotNull String input) {
        return deserialize(input, defaultType);
    }

    @Override
    public @NotNull Component deserialize(@NotNull String input, @NotNull ComponentSerializerType type) {
        ComponentCache cache = activeCache();
        String key = type.name() + "\u0000" + input;
        Component cached = cache == null ? null : cache.get(key, cachePolicy);
        if (cached != null) {
            return cached;
        }
        Component parsed = serializer(type).deserialize(input);
        if (cache != null) {
            cache.put(key, parsed, cachePolicy);
        }
        return parsed;
    }

    @Override
    public @NotNull List<Component> deserializeAll(@NotNull Iterable<String> inputs) {
        return deserializeAll(inputs, defaultType);
    }

    @Override
    public @NotNull List<Component> deserializeAll(@NotNull Iterable<String> inputs, @NotNull ComponentSerializerType type) {
        List<Component> result = new ArrayList<>();
        for (String input : inputs) {
            result.add(deserialize(input, type));
        }
        return result;
    }

    @Override
    public @NotNull Component deserializeLines(@NotNull Iterable<String> lines) {
        return deserializeLines(lines, defaultType);
    }

    @Override
    public @NotNull Component deserializeLines(@NotNull Iterable<String> lines, @NotNull ComponentSerializerType type) {
        Iterator<Component> iterator = deserializeAll(lines, type).iterator();
        if (!iterator.hasNext()) {
            return Component.empty();
        }
        Component result = iterator.next();
        while (iterator.hasNext()) {
            result = result.append(Component.newline()).append(iterator.next());
        }
        return result;
    }

    @Override
    public @NotNull String serialize(@NotNull Component component) {
        return serializer(defaultType).serialize(component);
    }

    @Override
    public @NotNull ComponentTemplate template(@NotNull String input) {
        return template(input, defaultType);
    }

    @Override
    public @NotNull ComponentTemplate template(@NotNull String input, @NotNull ComponentSerializerType type) {
        return new CachedTemplate(placeholders, input, serializer(type));
    }

    @Override
    public void configureCache(@NotNull ComponentCachePolicy policy) {
        cachePolicy = policy;
        pluginCache.trim(policy.getMaximumEntries());
    }

    @Override
    public void clearCache() {
        pluginCache.clear();
        if (cachePolicy.getScope() == ComponentCacheScope.GLOBAL) {
            sharedCache.clear();
        }
    }

    @Override
    public @NotNull ComponentCacheStatistics cacheStatistics() {
        return (cachePolicy.getScope() == ComponentCacheScope.GLOBAL ? sharedCache : pluginCache).statistics();
    }

    private ComponentCache activeCache() {
        switch (cachePolicy.getScope()) {
            case PLUGIN:
                return pluginCache;
            case GLOBAL:
                return sharedCache;
            default:
                return null;
        }
    }
}
