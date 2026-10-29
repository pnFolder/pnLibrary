package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity

/**
 * The Russian and English support note every configuration file written through pnLibrary carries.
 *
 * It goes right after the `_config-version` marker, or at the top when the file has none, and is added only once:
 * a file that already holds the support link is returned as it is.
 */
internal object SupportHeader {
    private val note = listOf(
        "# Если у вас возникли проблемы с плагином, пишите в наш Discord: мы всегда рады помочь.",
        "# If you have any problem with a plugin, write to our Discord: we are always happy to help.",
        "# ${PnLibraryIdentity.SUPPORT_URL}",
    )

    /** Returns [content] with the support note added when it is missing. */
    fun apply(content: String): String {
        if (PnLibraryIdentity.SUPPORT_URL in content) return content
        val lines = content.split('\n')
        val marker = lines.indexOfFirst { it.startsWith("_config-version:") }
        val at = marker + 1
        return (lines.take(at) + note + lines.drop(at)).joinToString("\n")
    }
}
