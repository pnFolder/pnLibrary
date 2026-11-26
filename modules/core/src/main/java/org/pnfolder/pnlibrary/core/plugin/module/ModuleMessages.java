package org.pnfolder.pnlibrary.core.plugin.module;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.logging.MessageBox;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMessages;

/** Plugin message helpers bound to one module owner. */
public final class ModuleMessages implements PluginMessages {
    private final Object owner;
    private final LoggingService logging;

    public ModuleMessages(@NotNull Object owner, @NotNull LoggingService logging) {
        this.owner = owner;
        this.logging = logging;
    }

    @Override
    public @NotNull MessageBox box(@NotNull String title) {
        if (title.trim().isEmpty()) {
            throw new IllegalArgumentException("Message box title must not be blank");
        }
        return logging.box(owner, title.trim());
    }
}
