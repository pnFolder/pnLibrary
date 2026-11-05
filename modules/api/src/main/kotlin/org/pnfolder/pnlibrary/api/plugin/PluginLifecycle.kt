package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.api.logging.MessageBox

/**
 * Symmetric, buffered startup and shutdown messages bound to one plugin.
 * Rows are collected in memory and printed together only when `MessageBox.show()` is called.
 */
interface PluginLifecycle {

    /** Metadata rendered into lifecycle summaries. */
    val metadata: PluginMetadata

    fun enabled(): MessageBox

    fun disabled(): MessageBox
}
