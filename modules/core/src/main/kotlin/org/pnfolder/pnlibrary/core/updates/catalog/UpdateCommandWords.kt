package org.pnfolder.pnlibrary.core.updates.catalog

/**
 * Words that follow `/pn update` and mean a step of the update flow, not a plugin.
 *
 * `/pn update <plugin>` updates a plugin, `/pn update confirm <token>` carries out a confirmation, and so on. The
 * command takes the word as a step first, so a plugin named like one of them could never be updated by name. Such
 * a name is refused when the plugin registers its updates.
 */
object UpdateCommandWords {
    /** The reserved words, in lower case. */
    val RESERVED: Set<String> = setOf("confirm", "status", "rollback", "restart")

    /** Whether [name] is one of the reserved words, ignoring case. */
    fun isReserved(name: String): Boolean = name.trim().lowercase() in RESERVED
}
