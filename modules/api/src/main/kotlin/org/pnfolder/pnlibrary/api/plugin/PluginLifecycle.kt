package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.api.logging.MessageBox

/**
 * Symmetric, buffered startup and shutdown messages bound to one plugin.
 * Rows are collected in memory and printed together only when `MessageBox.show()` is called.
 */
interface PluginLifecycle {

    /** Metadata rendered into lifecycle summaries. */
    val metadata: PluginMetadata

    /** Starts the "plugin enabled" card; call `show()` on it when startup is done. */
    fun enabled(): MessageBox

    /** Starts the "plugin disabled" card; call `show()` on it from `onDisable`. */
    fun disabled(): MessageBox
}
