package org.pnfolder.pnlibrary.api.placeholders.annotation

import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo

/**
 * Marks the parameter that receives the [ViewerInfo]: who asks, in which language, and through which way.
 * The library fills it; it is not a part of the address. `fun level(@Viewer viewer: ViewerInfo): Int?`
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Viewer
