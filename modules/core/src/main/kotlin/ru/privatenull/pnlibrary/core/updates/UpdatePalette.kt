package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

/**
 * Chat colors of the update catalog in the pnFolder studio palette: Electric Indigo as the main accent, Periwinkle
 * for highlights and the start of the title gradient, Cool Silver for text, and quiet grays for everything
 * secondary. There is no green, red or gold; states differ by indigo, periwinkle and orchid and by their words.
 *
 * Servers older than 1.16 cannot show RGB; Adventure then substitutes the nearest legacy chat color, matching hue
 * first, so any blue tint turns a color blue there. The silver and grays are therefore kept neutral, landing on
 * white, gray and dark gray, while indigo and periwinkle become blue and orchid becomes light purple.
 */
internal object UpdatePalette {
    /** Cool Silver: names, values and the main text; legacy white. */
    val TEXT: TextColor = TextColor.color(0xEEECEE)

    /** Labels, versions, brackets and descriptions; legacy gray. */
    val SOFT: TextColor = TextColor.color(0xBDB8BD)

    /** Commands and secondary details in hover cards; legacy gray. */
    val HINT: TextColor = TextColor.color(0xA09CA0)

    /** Options the reader may not choose; legacy dark gray. */
    val FAINT: TextColor = TextColor.color(0x6A646A)

    /** Electric Indigo: buttons, counters, the selected choice and prepared updates; legacy blue. */
    val ACCENT: TextColor = TextColor.color(0x635BFF)

    /** Periwinkle: new versions, success and everything up to date; legacy blue. */
    val GLOW: TextColor = TextColor.color(0xA49CFF)

    /** Orchid: errors and anything that needs attention, instead of red; legacy light purple. */
    val ALERT: TextColor = TextColor.color(0xE58BFF)

    /** [text] in the studio gradient, Periwinkle to Electric Indigo, one color per character. */
    fun gradient(text: String): Component {
        val last = maxOf(1, text.length - 1)
        return text.foldIndexed(Component.empty()) { index, line, char ->
            line.append(Component.text(char.toString(), TextColor.lerp(index.toFloat() / last, GLOW, ACCENT)))
        }
    }
}
