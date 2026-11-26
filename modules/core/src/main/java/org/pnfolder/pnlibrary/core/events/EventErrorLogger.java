package org.pnfolder.pnlibrary.core.events;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Receives the failures of listeners, so the library can put them in its error log. */
@FunctionalInterface
public interface EventErrorLogger {
    /** Records that code of {@code plugin} failed with {@code error}, described by {@code message}. */
    void log(@NotNull PluginId plugin, @NotNull String message, @NotNull Throwable error);
}
