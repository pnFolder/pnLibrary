package ru.privatenull.pnlibrary.api.diagnostics

/**
 * A handle returned by [DiagnosticsService.register].
 * Closing it removes the contributor from the registry — call this from
 * your plugin's `onDisable` to avoid stale contributors on hot-reload.
 */
fun interface DiagnosticRegistration : AutoCloseable {
    /** Whether this contributor has already been removed. */
    val isClosed: Boolean get() = false

    /** Removes the registered contributor; repeated calls are safe. */
    override fun close()
}
