package org.pnfolder.pnlibrary.api.placeholders.model

import java.util.concurrent.CompletionStage

/** Resolves one placeholder asynchronously; a null result selects its fallback. */
fun interface AsyncPlaceholderResolver<T : Any> {
    /** Starts resolution and returns its eventual value. */
    fun resolve(request: PlaceholderRequest): CompletionStage<T?>
}
