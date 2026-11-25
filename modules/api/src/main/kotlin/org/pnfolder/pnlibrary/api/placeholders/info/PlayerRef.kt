package org.pnfolder.pnlibrary.api.placeholders.info

import java.util.UUID

/** A player written in an address: a name or a UUID. Looking the name up is left to the plugin. */
class PlayerRef(val text: String) {
    /** The UUID when [text] is one, otherwise `null`. */
    val uuid: UUID? = runCatching { UUID.fromString(text) }.getOrNull()

    override fun toString(): String = text
}
