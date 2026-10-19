package org.pnfolder.pnlibrary.api.commands

/** Closeable handle for one live native command registration. */
interface CommandRegistration : AutoCloseable {
    /** Portable definition represented by this registration. */
    val command: CommandDefinition
    /** Whether this registration has already been closed. */
    val isClosed: Boolean
    /** Removes the native command and releases this registration. */
    override fun close()
}
