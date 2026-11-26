package org.pnfolder.pnlibrary.core.menu.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.jetbrains.annotations.NotNull;

/** How the menu's questions and answers are written as JSON on the link. */
public final class MenuWire {
    private static final GsonComponentSerializer CHAT = GsonComponentSerializer.gson();
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
        .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, type, context) -> Instant.parse(json.getAsString()))
        .registerTypeAdapter(Duration.class, (JsonSerializer<Duration>) (value, type, context) -> new JsonPrimitive(value.toMillis()))
        .registerTypeAdapter(Duration.class, (JsonDeserializer<Duration>) (json, type, context) -> Duration.ofMillis(json.getAsLong()))
        .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (value, type, context) -> new JsonPrimitive(value.toString()))
        .registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, type, context) -> LocalDate.parse(json.getAsString()))
        .create();

    private MenuWire() {
    }

    public static @NotNull Gson gson() {
        return GSON;
    }

    public static @NotNull List<String> encodeLines(@NotNull List<Component> lines) {
        List<String> texts = new ArrayList<>();
        for (Component line : lines) {
            texts.add(CHAT.serialize(line));
        }
        return texts;
    }

    /** The chat messages in {@code texts}; one that cannot be read is skipped. */
    public static @NotNull List<Component> decodeLines(@NotNull List<String> texts) {
        List<Component> lines = new ArrayList<>();
        for (String text : texts) {
            try {
                lines.add(CHAT.deserialize(text));
            } catch (RuntimeException unreadable) {
                continue;
            }
        }
        return lines;
    }

    /** The type {@code List<element>}, for reading a list answer. */
    public static @NotNull Type listOf(@NotNull Class<?> element) {
        return TypeToken.getParameterized(List.class, element).getType();
    }
}
