package ru.privatenull.pnlibrary.spi.platform

/** Metadata and compatibility checks for a native plugin owner. */
interface PlatformOwnerMetadata {
    fun ownerMetadata(owner: Any): PluginSnapshot? = null

    fun ownerDetails(owner: Any): Map<String, String> = ownerMetadata(owner)?.let {
        mapOf("id" to it.id, "name" to it.name, "version" to it.version,
            "authors" to it.authors.joinToString(", "))
    }.orEmpty()

    fun acceptsOwner(owner: Any): Boolean = ownerDetails(owner).isNotEmpty()
}
