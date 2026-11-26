package org.pnfolder.pnlibrary.api.text.serializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Bidirectional converter for one {@link ComponentSerializerType}.
 *
 * <p>Implementations must be safe for repeated use. Unless stated by an implementation,
 * malformed input may throw a format-specific runtime exception.
 */
public interface ComponentSerializer {
    /** The syntax handled by this serializer. */
    @NotNull ComponentSerializerType getType();

    /** Parses one {@code input} string into an Adventure component. */
    @NotNull Component deserialize(@NotNull String input);

    /** Deserializes every input independently and preserves list boundaries. */
    default @NotNull List<Component> deserializeAll(@NotNull Iterable<String> inputs) {
        List<Component> result = new ArrayList<>();
        for (String input : inputs) {
            result.add(deserialize(input));
        }
        return result;
    }

    /** Deserializes lines and joins them with real newline components, without a trailing newline. */
    default @NotNull Component deserializeLines(@NotNull Iterable<String> lines) {
        return ComponentLines.join(deserializeAll(lines));
    }

    /** Vararg convenience overload for {@link #deserializeLines(Iterable)}. */
    default @NotNull Component deserializeLines(@NotNull String... lines) {
        return deserializeLines(Arrays.asList(lines));
    }

    /** Serializes {@code component} using this serializer's syntax. */
    @NotNull String serialize(@NotNull Component component);

    /** Serializes every component independently and preserves list boundaries. */
    default @NotNull List<String> serializeAll(@NotNull Iterable<Component> components) {
        List<String> result = new ArrayList<>();
        for (Component component : components) {
            result.add(serialize(component));
        }
        return result;
    }
}
