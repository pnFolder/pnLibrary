package ru.privatenull.pnlibrary.api

/**
 * A handle returned by [DiagnosticsService.register].
 * Closing it removes the contributor from the registry — call this from
 * your plugin's `onDisable` to avoid stale contributors on hot-reload.
 */
fun interface DiagnosticRegistration : AutoCloseable {
    override fun close()
}
