package org.pnfolder.pnlibrary.spi.platform

import java.nio.file.Path
import java.util.logging.Logger

/** Metadata and compatibility checks for a native plugin owner. */
interface PlatformOwnerMetadata {
    /** Metadata of the native plugin [owner], or `null` when it is not a known plugin. */
    fun ownerMetadata(owner: Any): PluginSnapshot? = null

    /** Plugin id, name, version and authors of [owner] as strings; empty when unknown. */
    fun ownerDetails(owner: Any): Map<String, String> = ownerMetadata(owner)?.let {
        mapOf("id" to it.id, "name" to it.name, "version" to it.version,
            "authors" to it.authors.joinToString(", "))
    }.orEmpty()

    /** Whether [owner] is a plugin this platform recognises. */
    fun acceptsOwner(owner: Any): Boolean = ownerDetails(owner).isNotEmpty()

    /** The folder where [owner] keeps its files, or `null` when this platform gives plugins none (Velocity). */
    fun ownerDataFolder(owner: Any): Path? = null

    /** The logger of [owner], or `null` when this platform gives plugins none. */
    fun ownerLogger(owner: Any): Logger? = null
}
