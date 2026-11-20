package org.pnfolder.pnlibrary.config.yaml

/**
 * Cleans up the support note that older versions of pnLibrary wrote into every configuration file.
 *
 * The note (a Russian line, an English line and one or more link lines) is no longer written: help links live in the
 * generated files of the library's own folder. A file that still carries the note loses it the next time it is written.
 * Nothing but the note lines is touched.
 */
internal object LegacySupportNote {
    private val OLD_PREFIXES = listOf(
        "# Если у вас возникли проблемы с плагином",
        "# If you have any problem with a plugin",
    )
    private val LINK_LINE = Regex("^# (\\S+: )?https?://\\S+$")

    /** Returns [content] without any old support note; content without a note is returned unchanged. */
    fun removeFrom(content: String): String {
        val kept = ArrayList<String>()
        var inNote = false
        for (line in content.split('\n')) {
            val isNote = OLD_PREFIXES.any(line::startsWith) || (inNote && LINK_LINE.matches(line))
            inNote = isNote
            if (!isNote) kept.add(line)
        }
        return kept.joinToString("\n")
    }
}
