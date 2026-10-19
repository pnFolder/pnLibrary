package org.pnfolder.pnlibrary.bukkit.item

/** Converts legacy ampersand colors and RGB colors (`&#RRGGBB`) to item text. */
internal object ItemText {
    private val hexColor = Regex("&#([0-9a-f]{6})", RegexOption.IGNORE_CASE)

    /** Preserves absent text; Java item builders also call this formatter. */
    @JvmStatic
    fun color(text: String?): String? = text?.let {
        hexColor.replace(it) { match ->
            buildString {
                append('§').append('x')
                for (digit in match.groupValues[1]) append('§').append(digit)
            }
        }.replace('&', '§')
    }
}
