package ru.privatenull.pnlibrary.text

import java.util.regex.Pattern

/**
 * Text color utility supporting legacy `&` codes, section symbols `§`, and hex `&#RRGGBB` colors.
 */
object ColorUtil {

    private val HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})")
    private val LEGACY_HEX_PATTERN = Pattern.compile("&x&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])")

    @JvmStatic
    fun colorize(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        var text = input

        // Translate &#RRGGBB -> §x§R§R§G§G§B§B
        var matcher = HEX_PATTERN.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            val hex = matcher.group(1)
            val replacement = buildString {
                append("§x")
                for (ch in hex) {
                    append('§').append(ch)
                }
            }
            matcher.appendReplacement(sb, replacement)
        }
        matcher.appendTail(sb)
        text = sb.toString()

        // Translate &x&1&2&3&4&5&6 -> §x§1§2§3§4§5§6
        matcher = LEGACY_HEX_PATTERN.matcher(text)
        val sb2 = StringBuffer()
        while (matcher.find()) {
            val replacement = "§x§${matcher.group(1)}§${matcher.group(2)}§${matcher.group(3)}§${matcher.group(4)}§${matcher.group(5)}§${matcher.group(6)}"
            matcher.appendReplacement(sb2, replacement)
        }
        matcher.appendTail(sb2)
        text = sb2.toString()

        // Translate standard &0-9a-fk-or
        return text.replace('&', '§')
    }

    @JvmStatic
    fun stripColor(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        return input.replace(Regex("(?i)§[0-9A-FK-ORX]"), "")
    }
}
