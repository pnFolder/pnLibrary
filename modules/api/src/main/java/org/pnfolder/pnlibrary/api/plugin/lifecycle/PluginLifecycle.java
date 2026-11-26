package org.pnfolder.pnlibrary.api.plugin.lifecycle;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.MessageBox;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;

/**
 * Symmetric, buffered startup and shutdown messages bound to one plugin.
 * Rows are collected in memory and printed together only when {@code MessageBox.show()} is called.
 */
public interface PluginLifecycle {
    /** Metadata rendered into lifecycle summaries. */
    @NotNull
    PluginMetadata getMetadata();

    /** Starts the "plugin enabled" card; call {@code show()} on it when startup is done. */
    @NotNull
    MessageBox enabled();

    /** Starts the "plugin disabled" card; call {@code show()} on it from {@code onDisable}. */
    @NotNull
    MessageBox disabled();
}
