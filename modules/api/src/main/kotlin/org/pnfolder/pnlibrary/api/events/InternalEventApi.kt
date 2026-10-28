package org.pnfolder.pnlibrary.api.events

/**
 * Marks API that only the event bus itself may use. Using it without `@OptIn(InternalEventApi::class)` is a
 * compile error, so plugins cannot fake a dispatch mode by accident.
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "Only the pnLibrary event bus may call this.")
@Retention(AnnotationRetention.BINARY)
annotation class InternalEventApi
