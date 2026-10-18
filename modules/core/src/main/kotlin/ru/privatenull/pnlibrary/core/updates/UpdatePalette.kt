package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * RGB colors of the update catalog.
 *
 * Servers older than 1.16 cannot show RGB; Adventure then substitutes the nearest legacy chat color, so the
 * pages stay readable everywhere and look their best on current clients.
 */
internal object UpdatePalette {
    val ACCENT: TextColor = TextColor.color(0xB57BFF)
    val TEXT: TextColor = TextColor.color(0xF3F4F6)
    val MUTED: TextColor = TextColor.color(0x9CA3AF)
    val DIM: TextColor = TextColor.color(0x6B7280)
    val OK: TextColor = TextColor.color(0x4ADE80)
    val INFO: TextColor = TextColor.color(0x38BDF8)
    val INFO_DIM: TextColor = TextColor.color(0x0EA5E9)
    val WARN: TextColor = TextColor.color(0xFBBF24)
    val ORANGE: TextColor = TextColor.color(0xFB923C)
    val DANGER: TextColor = TextColor.color(0xF87171)

    private val GRADIENT_FROM: TextColor = TextColor.color(0x8B5CF6)
    private val GRADIENT_TO: TextColor = TextColor.color(0xE879F9)

    /** [text] with its color moving from violet to fuchsia, one color per character; bold. */
    fun gradient(text: String): Component {
        val last = maxOf(1, text.length - 1)
        return text.foldIndexed(Component.empty()) { index, line, char ->
            line.append(Component.text(char.toString(), TextColor.lerp(index.toFloat() / last, GRADIENT_FROM, GRADIENT_TO), TextDecoration.BOLD))
        }
    }
}
