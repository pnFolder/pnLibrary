package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo

/** The value of one placeholder registered without a class; see [PlaceholderService.value]. */
fun interface PlaceholderValue {
    /** The value for [viewer], or `null` when there is none. */
    fun get(viewer: ViewerInfo): Any?
}
