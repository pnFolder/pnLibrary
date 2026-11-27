package org.pnfolder.pnlibrary.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.console.ConsoleTheme;

/**
 * Console text styling: legacy section-sign sequences of each color's nearest legacy color, which every console
 * shows, including Windows terminals and 1.8 servers, and the one {@link ConsoleTheme} of every pnLibrary console card.
 *
 * <p>The sequences come from Adventure's legacy serializer, so console cards and downsampled chat always agree.
 */
public final class ConsoleStyle {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String PROBE = "|";

    /** Turns bold on until the next color or {@link #RESET}. */
    public static final String BOLD = format(Component.text(PROBE).decorate(TextDecoration.BOLD));

    /** Turns underline on until the next color or {@link #RESET}. */
    public static final String UNDERLINE = format(Component.text(PROBE).decorate(TextDecoration.UNDERLINED));

    public static final String RESET = LegacyComponentSerializer.SECTION_CHAR + "r";

    /** Every card: lavender borders, white labels and text, gray connectors; values follow the tone. */
    public static final ConsoleTheme THEME = theme(ChatPalette.TEXT);

    private ConsoleStyle() {
    }

    /** {@link #THEME} whose mascot, values and status line carry the tone's color, such as green for success. */
    public static @NotNull ConsoleTheme theme(@NotNull ChatTone tone) {
        return theme(tone.getColor());
    }

    /** The sequence that switches to the nearest legacy color of {@code color}. */
    public static @NotNull String color(@NotNull TextColor color) {
        return format(Component.text(PROBE, color));
    }

    public static @NotNull String paint(@NotNull String text, @NotNull TextColor color) {
        return color(color) + text + RESET;
    }

    private static ConsoleTheme theme(TextColor accent) {
        return new ConsoleTheme(color(ChatPalette.ACCENT), color(accent), color(ChatPalette.TEXT), color(ChatPalette.SOFT), RESET);
    }

    /** The style sequence the serializer writes in front of a one-character probe. */
    private static String format(Component probe) {
        String serialized = LEGACY.serialize(probe);
        return serialized.endsWith(PROBE) ? serialized.substring(0, serialized.length() - PROBE.length()) : serialized;
    }
}
