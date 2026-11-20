package org.pnfolder.pnlibrary.api.placeholders.tree

import java.util.UUID

/** A player written in a placeholder path: a name or a UUID. Name lookup is left to the plugin. */
class PlayerArg(val text: String) {
    /** The UUID when [text] is one, otherwise `null`. */
    val id: UUID? = runCatching { UUID.fromString(text) }.getOrNull()

    override fun toString(): String = text
}
