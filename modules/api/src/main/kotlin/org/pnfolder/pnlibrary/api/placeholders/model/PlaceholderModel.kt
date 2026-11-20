package org.pnfolder.pnlibrary.api.placeholders.model

/** Resolves one placeholder synchronously; `null` selects its configured fallback. */
fun interface PlaceholderResolver<T : Any> {
    /** Produces a value for [request], or `null` when no value is available. */
    fun resolve(request: PlaceholderRequest): T?
}
