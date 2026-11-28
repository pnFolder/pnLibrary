package org.pnfolder.pnlibrary.core.menu.owner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Finds the plugin behind an object, a class or a name. */
public interface PluginDirectory {
    /** The plugin that {@code owner} is, or null when it is not one. */
    @Nullable OwnerInfo owner(@Nullable Object owner);

    @Nullable OwnerInfo ownerNamed(@NotNull String name);

    /** The plugin whose code defines {@code type}, or null. */
    @Nullable OwnerInfo ownerOf(@Nullable Class<?> type);
}
