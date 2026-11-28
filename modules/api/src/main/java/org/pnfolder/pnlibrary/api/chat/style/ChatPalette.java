package org.pnfolder.pnlibrary.api.chat.style;

import net.kyori.adventure.text.format.TextColor;

/**
 * The colors of every pnLibrary chat message, after the Catalog plugin: a soft lavender accent for titles, numbers
 * and the current choice, white names, lilac labels and hints, a neutral gray for versions and brackets, and
 * calm semantic colors for states.
 *
 * <p>Servers older than 1.16 cannot show RGB; Adventure then substitutes the nearest legacy chat color, matching hue
 * first. Every role lands on its own legacy color there: text on white, versions on gray, unavailable options on
 * dark gray, the accent and labels on light purple, and the semantic colors on green, aqua, yellow, gold and red.
 */
public final class ChatPalette {
    /** Names and values; legacy white. */
    public static final TextColor TEXT = TextColor.color(0xF7F2F7);

    /** Versions, brackets and separators; a neutral gray, legacy gray. */
    public static final TextColor SOFT = TextColor.color(0xBFB5C0);

    /** Options the reader may not choose; legacy dark gray. */
    public static final TextColor FAINT = TextColor.color(0x64546E);

    /** Titles, counters, day headings and navigation buttons; a soft lavender, legacy light purple. */
    public static final TextColor ACCENT = TextColor.color(0xD19BFF);

    /** Field labels, hints and descriptions; a soft lilac that sits beside the accent instead of competing with it. */
    public static final TextColor LABEL = TextColor.color(0xDDB8F5);

    /** Success, up to date, turned on; legacy green. */
    public static final TextColor OK = TextColor.color(0x6EE7A0);

    /** New versions and neutral facts; legacy aqua. */
    public static final TextColor INFO = TextColor.color(0x5BC8F5);

    /** Pauses and things to keep an eye on; legacy yellow. */
    public static final TextColor WARN = TextColor.color(0xFDE047);

    /** Prepared work waiting for a restart; legacy gold. */
    public static final TextColor ORANGE = TextColor.color(0xFFA53A);

    /** Errors, turned off and risky actions; legacy red. */
    public static final TextColor DANGER = TextColor.color(0xFF7A7A);

    private ChatPalette() {
    }
}
