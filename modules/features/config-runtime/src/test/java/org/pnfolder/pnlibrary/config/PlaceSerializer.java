package org.pnfolder.pnlibrary.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;

/** Writes a place as {@code world,x}. */
public final class PlaceSerializer implements ConfigSerializer<Place> {
    private final String tag;

    public PlaceSerializer() {
        this("");
    }

    public PlaceSerializer(String tag) {
        this.tag = tag;
    }

    @Override
    @NotNull
    public Object serialize(@NotNull Place value, @NotNull ConfigSerializationContext context) {
        return tag + value.world + "," + value.x;
    }

    @Override
    @NotNull
    public Place deserialize(@Nullable Object value, @NotNull ConfigSerializationContext context) {
        String text = String.valueOf(value);
        String[] parts = (text.startsWith(tag) ? text.substring(tag.length()) : text).split(",");
        return new Place(parts[0], Integer.parseInt(parts[1]));
    }
}
