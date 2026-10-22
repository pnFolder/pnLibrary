package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.api.logging.MessageBox

/**
 * Symmetric, buffered startup and shutdown messages bound to one plugin.
 * Rows are collected in memory and printed together only when `MessageBox.show()` is called.
 */
interface PluginLifecycle {

    /** Metadata rendered into lifecycle summaries. */
    val metadata: PluginMetadata

    /** Creates an enabled message with name and version filled from [metadata]. */
    fun enabled(): MessageBox

    /** Creates a disabled message with name and version filled from [metadata]. */
    fun disabled(): MessageBox

    /** Like [enabled], without the rows pnLibrary would add for [hide]; the plugin's own rows are never hidden. */
    fun enabled(vararg hide: LifecycleRow): MessageBox

    /** Like [disabled], without the rows pnLibrary would add for [hide]; the plugin's own rows are never hidden. */
    fun disabled(vararg hide: LifecycleRow): MessageBox
}
