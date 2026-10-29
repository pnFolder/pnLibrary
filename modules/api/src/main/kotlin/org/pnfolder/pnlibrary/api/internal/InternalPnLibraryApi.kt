package org.pnfolder.pnlibrary.api.internal

/**
 * Marks public declarations that exist only so pnLibrary's own modules can reach them, such as the serializers
 * behind [org.pnfolder.pnlibrary.api.checks.Check]. They may change or disappear in any release, so using one
 * without `@OptIn(InternalPnLibraryApi::class)` is a compile error in Kotlin.
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "This is internal to pnLibrary and may change without notice.")
@Retention(AnnotationRetention.BINARY)
annotation class InternalPnLibraryApi
