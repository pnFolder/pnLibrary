package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.placeholders.model.PlaceholderRequest

/**
 * Applies a textual assignment to a typed placeholder-owned value.
 *
 * The owner validates and converts the supplied value, persists it in its own storage, and
 * returns the resulting typed value. Throwing rejects the update.
 */
fun interface PlaceholderUpdater<T : Any> {
    /** Updates the backing value and returns its new typed representation. */
    fun update(request: PlaceholderRequest, value: String): T?
}
