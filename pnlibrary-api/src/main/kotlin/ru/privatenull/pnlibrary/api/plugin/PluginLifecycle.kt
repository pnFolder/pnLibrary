package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.logging.MessageBox

/** Symmetric startup and shutdown messages bound to one registered plugin. */
interface PluginLifecycle {
    val metadata: PluginMetadata

    /** Creates an enabled message with name and version filled from [metadata]. */
    fun enabled(): MessageBox

    /** Creates a disabled message with name and version filled from [metadata]. */
    fun disabled(): MessageBox
}
