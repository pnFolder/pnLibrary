package org.pnfolder.pnlibrary.core.menu;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A host that runs everything at once and knows no plugins. */
final class ImmediateHost implements MenuHost {
    @Override
    public void sync(@NotNull Runnable action) {
        action.run();
    }

    @Override
    public void later(long delayMillis, @NotNull Runnable action) {
        action.run();
    }

    @Override
    public void async(@NotNull Runnable action) {
        action.run();
    }

    @Override
    public boolean openRemote(@NotNull MenuViewer viewer, @NotNull String target) {
        return false;
    }

    @Override
    public @Nullable OwnerInfo owner(@Nullable Object owner) {
        return null;
    }

    @Override
    public @Nullable OwnerInfo ownerNamed(@NotNull String name) {
        return null;
    }

    @Override
    public @Nullable OwnerInfo ownerOf(@Nullable Class<?> type) {
        return null;
    }
}
