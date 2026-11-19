package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.placeholders.model.PlaceholderRequest

/** Converts a resolved typed value into the string requested by a format expression. */
fun interface PlaceholderFormatter<T : Any> {
    /** Formats [value] using parsed [arguments] and the original [request]. */
    fun format(value: T, arguments: List<String>, request: PlaceholderRequest): String
}
