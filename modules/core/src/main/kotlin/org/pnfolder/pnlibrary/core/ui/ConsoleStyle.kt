package org.pnfolder.pnlibrary.core.ui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.chat.color
import org.pnfolder.pnlibrary.console.ConsoleTheme

/**
 * [ChatPalette] for console text: legacy section-sign sequences of each color's nearest legacy color, which every console
 * shows, including Windows terminals and 1.8 servers, and the one [ConsoleTheme] of every pnLibrary console card.
 *
 * The sequences come from Adventure's legacy serializer, so console cards and downsampled chat always agree.
 */
object ConsoleStyle {
    private val LEGACY = LegacyComponentSerializer.legacySection()
    private const val PROBE = "|"

    /** Turns bold on until the next color or [RESET]. */
    @JvmField val BOLD: String = format(Component.text(PROBE).decorate(TextDecoration.BOLD))

    /** Turns underline on until the next color or [RESET]. */
    @JvmField val UNDERLINE: String = format(Component.text(PROBE).decorate(TextDecoration.UNDERLINED))

    /** Restores the console's default style. */
    @JvmField val RESET: String = "${LegacyComponentSerializer.SECTION_CHAR}r"

    /** Every card: lavender borders, white labels and text, gray connectors; values follow the tone. */
    @JvmField val THEME: ConsoleTheme = theme(ChatPalette.TEXT)

    /** [THEME] whose mascot, values and status line carry the [tone]'s color, such as green for success. */
    @JvmStatic
    fun theme(tone: ChatTone): ConsoleTheme = theme(tone.color)

    /** The sequence that switches to the nearest legacy color of [color]. */
    @JvmStatic
    fun color(color: TextColor): String = format(Component.text(PROBE, color))

    /** [text] in [color], followed by [RESET]. */
    @JvmStatic
    fun paint(text: String, color: TextColor): String = color(color) + text + RESET

    private fun theme(accent: TextColor): ConsoleTheme =
        ConsoleTheme(color(ChatPalette.ACCENT), color(accent), color(ChatPalette.TEXT), color(ChatPalette.SOFT), RESET)

    /** The style sequence the serializer writes in front of a one-character probe. */
    private fun format(probe: Component): String = LEGACY.serialize(probe).removeSuffix(PROBE)
}
