package org.pnfolder.pnlibrary.api.plugin.metadata;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.MessageBox;

/** Console message boxes bound to one plugin. */
public interface PluginMessages {
    /** Starts a console block titled {@code title}; nothing is printed until {@code show()}. */
    @NotNull
    MessageBox box(@NotNull String title);
}
