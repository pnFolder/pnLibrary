package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

/**
 * RGB colors of the update catalog: violet-tinted neutrals for text and calm semantic colors for states.
 *
 * Servers older than 1.16 cannot show RGB; Adventure then substitutes the nearest legacy chat color by hue
 * first. Every color is tuned so that each role still lands on its own legacy color there: text on white,
 * secondary text on gray, hints on blue, separators on dark gray, the accent on light purple, and the
 * semantic colors on green, aqua, yellow, gold and red.
 */
internal object UpdatePalette {
    /** Primary text such as plugin names and values; legacy white. */
    val TEXT: TextColor = TextColor.color(0xF7F2F7)

    /** Secondary text: labels, current versions, descriptions; a soft mauve-lavender, legacy gray. */
    val SOFT: TextColor = TextColor.color(0xCDBACF)

    /** Hints, times, actors and commands; a cool periwinkle, legacy blue. */
    val HINT: TextColor = TextColor.color(0x93A8E0)

    /** Separators between parts of a line; a dim violet, legacy dark gray. */
    val FAINT: TextColor = TextColor.color(0x64546E)

    /** Navigation buttons and day headings; a soft orchid, legacy light purple. */
    val ACCENT: TextColor = TextColor.color(0xD19BFF)

    val OK: TextColor = TextColor.color(0x4ADE80)
    val INFO: TextColor = TextColor.color(0x38BDF8)
    val WARN: TextColor = TextColor.color(0xFDE047)
    val ORANGE: TextColor = TextColor.color(0xFFA53A)
    val DANGER: TextColor = TextColor.color(0xF87171)

    private val GRADIENT_FROM: TextColor = TextColor.color(0x8B5CF6)
    private val GRADIENT_TO: TextColor = TextColor.color(0xE879F9)

    /** [text] with its color moving from violet to fuchsia, one color per character. */
    fun gradient(text: String): Component {
        val last = maxOf(1, text.length - 1)
        return text.foldIndexed(Component.empty()) { index, line, char ->
            line.append(Component.text(char.toString(), TextColor.lerp(index.toFloat() / last, GRADIENT_FROM, GRADIENT_TO)))
        }
    }
}
