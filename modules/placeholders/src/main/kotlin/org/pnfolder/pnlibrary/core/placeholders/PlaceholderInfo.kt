package org.pnfolder.pnlibrary.core.placeholders

/** One placeholder as the administrator sees it in `/pn placeholders`. */
data class PlaceholderInfo(
    val address: String,
    val type: String,
    val format: String?,
    val published: List<String>,
    val enabled: Boolean,
)
