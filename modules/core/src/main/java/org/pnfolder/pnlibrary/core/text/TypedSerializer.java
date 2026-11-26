package org.pnfolder.pnlibrary.core.text;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializer;
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** The serializer of one {@link ComponentSerializerType}; Adventure serializers are created on first use. */
final class TypedSerializer implements ComponentSerializer {
    private final ComponentSerializerType type;
    private final MiniText mini;
    private final Consumer<String> warn;

    TypedSerializer(ComponentSerializerType type, MiniText mini, Consumer<String> warn) {
        this.type = type;
        this.mini = mini;
        this.warn = warn;
    }

    @Override
    public @NotNull ComponentSerializerType getType() {
        return type;
    }

    @Override
    public @NotNull Component deserialize(@NotNull String input) {
        switch (type) {
            case ADAPTIVE:
                return adaptive(input);
            case MINI_MESSAGE:
                return mini.deserialize(input, warn);
            case LEGACY_AMPERSAND:
                return Serializers.amp().deserialize(input);
            case LEGACY_SECTION:
                return Serializers.section().deserialize(input);
            case ADVENTURE_JSON:
                return Serializers.gson().deserialize(input);
            default:
                return Component.text(input);
        }
    }

    @Override
    public @NotNull String serialize(@NotNull Component component) {
        switch (type) {
            case ADAPTIVE:
            case MINI_MESSAGE:
                return mini.serialize(component, warn, Serializers.amp()::serialize);
            case LEGACY_AMPERSAND:
                return Serializers.amp().serialize(component);
            case LEGACY_SECTION:
                return Serializers.section().serialize(component);
            case ADVENTURE_JSON:
                return Serializers.gson().serialize(component);
            default:
                return Serializers.plain().serialize(component);
        }
    }

    private Component adaptive(String input) {
        if (input.trim().isEmpty()) {
            return Component.empty();
        }
        String trimmed = input.replaceAll("^\\s+", "");
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            Attempt<Component> json = Failures.runCatchingNonFatal(() -> Serializers.gson().deserialize(input));
            if (json.isSuccess()) {
                return json.getOrNull();
            }
        }
        return mini.deserialize(LegacyToMini.convert(input), warn);
    }

    /** Lazily created so servers with an old Adventure fail only when a type is really used. */
    private static final class Serializers {
        private static LegacyComponentSerializer amp;
        private static LegacyComponentSerializer section;
        private static GsonComponentSerializer gson;
        private static PlainTextComponentSerializer plain;

        static synchronized LegacyComponentSerializer amp() {
            if (amp == null) {
                amp = LegacyComponentSerializer.legacyAmpersand();
            }
            return amp;
        }

        static synchronized LegacyComponentSerializer section() {
            if (section == null) {
                section = LegacyComponentSerializer.legacySection();
            }
            return section;
        }

        static synchronized GsonComponentSerializer gson() {
            if (gson == null) {
                gson = GsonComponentSerializer.gson();
            }
            return gson;
        }

        static synchronized PlainTextComponentSerializer plain() {
            if (plain == null) {
                plain = PlainTextComponentSerializer.plainText();
            }
            return plain;
        }
    }
}
