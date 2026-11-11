package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity

/**
 * The Russian and English support note every configuration file written through pnLibrary carries.
 *
 * It goes right after the `_config-version` marker, or at the top when the file has none. The note is rebuilt from
 * [CHANNELS] every time, so a file with an older note (one channel fewer, an old link) gets the current one instead
 * of a second copy. To add a support platform, add one line to [CHANNELS]; every configuration gets it on the next load.
 */
internal object SupportHeader {
    /** One place where administrators get help. */
    class Channel(val name: String, val url: String)

    /** The support platforms listed in every configuration, in this order. */
    val CHANNELS: List<Channel> = listOf(
        Channel("Discord", PnLibraryIdentity.SUPPORT_URL),
    )

    private const val RUSSIAN = "# Если у вас возникли проблемы с плагином, пишите нам: мы всегда рады помочь."
    private const val ENGLISH = "# If you have any problem with a plugin, write to us: we are always happy to help."
    private val OLD_PREFIXES = listOf(
        "# Если у вас возникли проблемы с плагином",
        "# If you have any problem with a plugin",
    )
    private val LINK_LINE = Regex("^# (\\S+: )?https?://\\S+$")

    private val note: List<String> = listOf(RUSSIAN, ENGLISH) + CHANNELS.map { "# ${it.name}: ${it.url}" }

    /** Returns [content] with exactly one current support note. */
    fun apply(content: String): String {
        val lines = withoutNote(content.split('\n'))
        val at = lines.indexOfFirst { it.startsWith("_config-version:") } + 1
        return (lines.take(at) + note + lines.drop(at)).joinToString("\n")
    }

    private fun withoutNote(lines: List<String>): List<String> {
        val kept = ArrayList<String>(lines.size)
        var inNote = false
        for (line in lines) {
            val isNote = OLD_PREFIXES.any(line::startsWith) || (inNote && LINK_LINE.matches(line))
            inNote = isNote
            if (!isNote) kept.add(line)
        }
        return kept
    }
}
