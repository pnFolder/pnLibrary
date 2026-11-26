package org.pnfolder.pnlibrary.api.text;

import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCachePolicy;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCacheStatistics;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializer;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;

/**
 * Central text conversion, templating, and cache service.
 *
 * <p>Overloads without an explicit serializer use {@link #getDefaultSerializerType()}. Returned
 * components are Adventure values and can be safely further composed by callers.
 */
public interface ComponentService {
    /** The serializer selected by overloads that do not accept an explicit type. */
    @NotNull ComponentSerializerType getDefaultSerializerType();

    /** Selects the serializer used by overloads that do not accept an explicit type. */
    void setDefaultSerializerType(@NotNull ComponentSerializerType type);

    /** Returns the registered serializer for {@code type}. */
    @NotNull ComponentSerializer serializer(@NotNull ComponentSerializerType type);

    /** Parses {@code input} with {@link #getDefaultSerializerType()}. */
    @NotNull Component deserialize(@NotNull String input);

    /** Parses {@code input} with the selected serializer {@code type}. */
    @NotNull Component deserialize(@NotNull String input, @NotNull ComponentSerializerType type);

    /** Returns one component per source string. */
    @NotNull List<Component> deserializeAll(@NotNull Iterable<String> inputs);

    /** Parses each input independently with {@code type}. */
    @NotNull List<Component> deserializeAll(@NotNull Iterable<String> inputs, @NotNull ComponentSerializerType type);

    /** Returns one multiline component with {@code Component.newline()} between source strings. */
    @NotNull Component deserializeLines(@NotNull Iterable<String> lines);

    /** Parses and joins {@code lines} with explicit serializer {@code type}. */
    @NotNull Component deserializeLines(@NotNull Iterable<String> lines, @NotNull ComponentSerializerType type);

    /** Vararg convenience overload using {@link #getDefaultSerializerType()}. */
    default @NotNull Component deserializeLines(@NotNull String... lines) {
        return deserializeLines(Arrays.asList(lines));
    }

    /** Serializes {@code component} with {@link #getDefaultSerializerType()}. */
    @NotNull String serialize(@NotNull Component component);

    /** Serializes each component independently with {@link #getDefaultSerializerType()}. */
    default @NotNull List<String> serializeAll(@NotNull Iterable<Component> components) {
        List<String> result = new java.util.ArrayList<String>();
        for (Component component : components) {
            result.add(serialize(component));
        }
        return result;
    }

    /** Parses {@code input} into a reusable template using the default serializer. */
    @NotNull ComponentTemplate template(@NotNull String input);

    /** Parses {@code input} into a reusable template; the serializer type is ignored. */
    default @NotNull ComponentTemplate template(@NotNull String input, @NotNull ComponentSerializerType type) {
        return template(input);
    }

    /** Replaces the active parsed-component cache policy. */
    void configureCache(@NotNull ComponentCachePolicy policy);

    /** Removes all parsed components from the active cache. */
    void clearCache();

    /** Returns current cache counters and retained size. */
    @NotNull ComponentCacheStatistics cacheStatistics();
}
