package org.pnfolder.pnlibrary.api.chat

import net.kyori.adventure.text.format.TextColor

/**
 * The colors of every pnLibrary chat message, after the Catalog plugin: a soft lavender accent for titles, numbers
 * and the current choice, white names, lilac labels and hints, a neutral gray for versions and brackets, and
 * calm semantic colors for states.
 *
 * Servers older than 1.16 cannot show RGB; Adventure then substitutes the nearest legacy chat color, matching hue
 * first. Every role lands on its own legacy color there: text on white, versions on gray, unavailable options on
 * dark gray, the accent and labels on light purple, and the semantic colors on green, aqua, yellow, gold and red.
 */
object ChatPalette {
    /** Names and values; legacy white. */
    @JvmField val TEXT: TextColor = TextColor.color(0xF7F2F7)

    /** Versions, brackets and separators; a neutral gray, legacy gray. */
    @JvmField val SOFT: TextColor = TextColor.color(0xBFB5C0)

    /** Options the reader may not choose; legacy dark gray. */
    @JvmField val FAINT: TextColor = TextColor.color(0x64546E)

    /** Titles, counters, day headings and navigation buttons; a soft lavender, legacy light purple. */
    @JvmField val ACCENT: TextColor = TextColor.color(0xD19BFF)

    /** Field labels, hints and descriptions; a soft lilac that sits beside the accent instead of competing with it. */
    @JvmField val LABEL: TextColor = TextColor.color(0xDDB8F5)

    /** Success, up to date, turned on; legacy green. */
    @JvmField val OK: TextColor = TextColor.color(0x6EE7A0)

    /** New versions and neutral facts; legacy aqua. */
    @JvmField val INFO: TextColor = TextColor.color(0x5BC8F5)

    /** Pauses and things to keep an eye on; legacy yellow. */
    @JvmField val WARN: TextColor = TextColor.color(0xFDE047)

    /** Prepared work waiting for a restart; legacy gold. */
    @JvmField val ORANGE: TextColor = TextColor.color(0xFFA53A)

    /** Errors, turned off and risky actions; legacy red. */
    @JvmField val DANGER: TextColor = TextColor.color(0xFF7A7A)
}
