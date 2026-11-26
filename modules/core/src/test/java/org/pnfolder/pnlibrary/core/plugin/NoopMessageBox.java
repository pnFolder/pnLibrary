package org.pnfolder.pnlibrary.core.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.MessageBox;

/** A message box that ignores everything. */
public final class NoopMessageBox implements MessageBox {
    @Override
    public @NotNull MessageBox ok(@NotNull String label, @NotNull String detail) {
        return this;
    }

    @Override
    public @NotNull MessageBox warn(@NotNull String label, @NotNull String detail) {
        return this;
    }

    @Override
    public @NotNull MessageBox skip(@NotNull String label, @NotNull String detail) {
        return this;
    }

    @Override
    public @NotNull MessageBox fail(@NotNull String label, @NotNull String detail, @Nullable Throwable error) {
        return this;
    }

    @Override
    public void show() {
    }
}
