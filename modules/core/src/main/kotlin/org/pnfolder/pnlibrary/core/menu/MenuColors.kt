package org.pnfolder.pnlibrary.core.menu

/**
 * Colours of the menu text, written as the `§x§R§R§G§G§B§B` form that every platform understands.
 *
 * A server older than 1.16 does not know the form; its adapter turns it into the nearest basic colour before it shows
 * the text, so the menu can be built once and shown anywhere.
 */
object MenuColors {
    /** The sign that starts a formatting code. */
    const val SECTION: Char = '§'

    private const val RADIX = 16
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private val hexPattern = Regex("&#([0-9A-Fa-f]{6})")

    /** Turns `&#RRGGBB` in a template into hex colour codes. */
    fun color(template: String): String = hexPattern.replace(template) { match -> hex(match.groupValues[1]) }

    /** One colour code for [rgb], six hex digits. */
    fun hex(rgb: String): String = SECTION + "x" + rgb.map { "$SECTION$it" }.joinToString("")

    /** A colour code for the basic colour [code], one of `0`-`9` and `a`-`f`. */
    fun legacy(code: Char): String = "$SECTION$code"

    /** [text] whose colour moves from [from] to [to] (both `RRGGBB`) letter by letter. */
    fun gradient(text: String, from: String, to: String): String {
        if (text.length < 2) return hex(from) + text
        val start = from.toInt(RADIX)
        val end = to.toInt(RADIX)
        return buildString {
            text.forEachIndexed { index, char ->
                val step = index.toDouble() / (text.length - 1)
                val rgb = intArrayOf(RED_SHIFT, GREEN_SHIFT, 0).map { shift ->
                    val a = start shr shift and 0xFF
                    val b = end shr shift and 0xFF
                    (a + (b - a) * step).toInt()
                }
                append(hex("%02X%02X%02X".format(rgb[0], rgb[1], rgb[2]))).append(char)
            }
        }
    }
}
