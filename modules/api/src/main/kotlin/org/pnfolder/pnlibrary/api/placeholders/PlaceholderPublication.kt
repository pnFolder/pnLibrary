package org.pnfolder.pnlibrary.api.placeholders

/**
 * Requested external name for a library placeholder.
 *
 * @property adapterId case-insensitive adapter identifier, such as `placeholderapi`
 * @property namespace external namespace override, or `null` for the adapter default
 * @property name external placeholder name override, or `null` for the library key
 */
data class PlaceholderPublication(
    val adapterId: String,
    val namespace: String? = null,
    val name: String? = null,
)
