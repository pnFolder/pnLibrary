package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.format.TextColor

/**
 * RGB colors of the update catalog, after the Catalog plugin: a soft lavender accent for titles, numbers and the
 * selected choice, white names, neutral grays for everything secondary and calm semantic colors for states.
 *
 * Servers older than 1.16 cannot show RGB; Adventure then substitutes the nearest legacy chat color. Every role
 * lands on its own legacy color there: text on white, secondary text and hints on gray, disabled text on dark
 * gray, the accent on light purple, and the semantic colors on green, aqua, yellow, gold and red.
 */
internal object UpdatePalette {
    /** Names and values; legacy white. */
    val TEXT: TextColor = TextColor.color(0xF7F2F7)

    /** Labels, versions, brackets and descriptions; a neutral gray, legacy gray. */
    val SOFT: TextColor = TextColor.color(0xBFB5C0)

    /** Commands and secondary details in hover cards; a slightly darker gray, legacy gray. */
    val HINT: TextColor = TextColor.color(0xA89EAA)

    /** Options the reader may not choose; legacy dark gray. */
    val FAINT: TextColor = TextColor.color(0x64546E)

    /** Titles, counters, day headings and the selected choice; a soft lavender, legacy light purple. */
    val ACCENT: TextColor = TextColor.color(0xD19BFF)

    val OK: TextColor = TextColor.color(0x6EE7A0)
    val INFO: TextColor = TextColor.color(0x5BC8F5)
    val WARN: TextColor = TextColor.color(0xFDE047)
    val ORANGE: TextColor = TextColor.color(0xFFA53A)
    val DANGER: TextColor = TextColor.color(0xFF7A7A)
}
